package com.receipt.service;

import com.receipt.enums.ItemizeStatus;
import com.receipt.enums.ProcessingStatus;
import com.receipt.exception.ItemizationInProgressException;
import com.receipt.exception.PatchInProgressException;
import com.receipt.exception.ReceiptProcessingException;
import com.receipt.exception.TransactionNotFoundException;
import com.receipt.exception.TransactionReconciliationException;
import com.receipt.model.LineItem;
import com.receipt.model.Transaction;
import com.receipt.parser.ParsedDocument;
import com.receipt.parser.ReceiptTextParser;
import com.receipt.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final ReceiptTextParser receiptTextParser;
    private final TransactionItemizationUnit itemizationUnit;
    private final TransactionPatchUnit patchUnit;

    public TransactionService(
            TransactionRepository transactionRepository,
            ReceiptTextParser receiptTextParser,
            TransactionItemizationUnit itemizationUnit,
            TransactionPatchUnit patchUnit
    ) {
        this.transactionRepository = transactionRepository;
        this.receiptTextParser = receiptTextParser;
        this.itemizationUnit = itemizationUnit;
        this.patchUnit = patchUnit;
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(String transactionId) {
        log.debug("Loading transaction {}", transactionId);
        return loadWithChildren(transactionId);
    }

    /**
     * Re-runs auto-itemization against the stored OCR text on the transaction row,
     * replacing the current line items with a fresh parse result.
     *
     * No outer @Transactional — same two-transaction pattern as processReceipt:
     *   Tx-Claim: UPDATE itemize_status = ITEMIZING (own auto-commit, concurrency guard).
     *   Tx-Work:  @Transactional replaceAndComplete clears + inserts + sets status atomically.
     *   Tx-Fail:  markItemizeFailed auto-commits in catch, survives Tx-Work rollback.
     */
    public Transaction reItemize(String transactionId) {
        log.info("Re-itemizing transaction {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (transaction.getProcessingStatus() != ProcessingStatus.PROCESSED) {
            throw new ReceiptProcessingException(
                    "Transaction must be PROCESSED before itemizing. Current status: "
                    + transaction.getProcessingStatus());
        }

        boolean claimed = transactionRepository.claimForItemizing(transactionId);
        if (!claimed) {
            throw new ItemizationInProgressException(transactionId);
        }

        String rawOcrText = transaction.getRawOcrText();
        if (rawOcrText == null || rawOcrText.isBlank()) {
            transactionRepository.markItemizeFailed(transactionId);
            throw new ReceiptProcessingException("Transaction has no stored OCR text: " + transactionId);
        }

        ParsedDocument parsed = receiptTextParser.parse(rawOcrText);

        try {
            Transaction result = itemizationUnit.replaceAndComplete(
                    transactionId, parsed.getLineItems(), parsed.getItemizeStatus());
            log.info("Re-itemized transaction {} with {} line items",
                    transactionId, result.getLineItems().size());
            return loadWithChildren(transactionId);
        } catch (Exception ex) {
            transactionRepository.markItemizeFailed(transactionId);
            log.error("Failed to re-itemize transaction {}: {}", transactionId, ex.getMessage(), ex);
            throw new ReceiptProcessingException("Failed to re-itemize transaction: " + transactionId, ex);
        }
    }

    /**
     * Edits only the provided items in-place. Other items are unchanged.
     * Reconciliation is validated against the full final item list before claiming the lock.
     */
    public Transaction editItems(String transactionId, List<EditItemRequest> edits) {
        Transaction transaction = loadWithChildren(transactionId);
        Map<String, LineItem> existingById = indexById(transaction);

        for (EditItemRequest edit : edits) {
            if (!existingById.containsKey(edit.id())) {
                throw new IllegalArgumentException("Line item not found: " + edit.id());
            }
        }

        Map<String, EditItemRequest> editById = new HashMap<>();
        for (EditItemRequest e : edits) editById.put(e.id(), e);

        List<LineItem> finalItems = new ArrayList<>();
        for (LineItem existing : transaction.getLineItems()) {
            EditItemRequest edit = editById.get(existing.getId());
            LineItem item = copyLineItem(existing);
            if (edit != null) {
                if (edit.description() != null) item.setDescription(edit.description());
                if (edit.amount() != null) item.setAmount(edit.amount());
                if (edit.quantity() != null) item.setQuantity(edit.quantity());
            }
            finalItems.add(item);
        }

        validateReconciliation(transactionId, finalItems, transaction);
        claimPatch(transactionId);

        try {
            patchUnit.apply(transactionId, finalItems);
            log.info("Edited {} item(s) on transaction {}", edits.size(), transactionId);
            return loadWithChildren(transactionId);
        } catch (Exception ex) {
            transactionRepository.markPatchFailed(transactionId);
            log.error("Failed to edit items on transaction {}: {}", transactionId, ex.getMessage(), ex);
            throw new ReceiptProcessingException("Failed to edit items for transaction: " + transactionId, ex);
        }
    }

    /**
     * Deletes the given item IDs and inserts a single merged item.
     * Reconciliation is validated against the final item list before claiming the lock.
     */
    public Transaction mergeItems(String transactionId, List<String> mergeIds,
                                   String description, BigDecimal amount) {
        Transaction transaction = loadWithChildren(transactionId);
        Map<String, LineItem> existingById = indexById(transaction);

        Set<String> mergeSet = new HashSet<>(mergeIds);
        for (String id : mergeSet) {
            if (!existingById.containsKey(id)) {
                throw new IllegalArgumentException("Line item not found: " + id);
            }
        }

        List<LineItem> finalItems = new ArrayList<>();
        for (LineItem existing : transaction.getLineItems()) {
            if (!mergeSet.contains(existing.getId())) finalItems.add(copyLineItem(existing));
        }
        LineItem merged = new LineItem();
        merged.setId(UUID.randomUUID().toString());
        merged.setDescription(description);
        merged.setAmount(amount);
        finalItems.add(merged);

        validateReconciliation(transactionId, finalItems, transaction);
        claimPatch(transactionId);

        try {
            patchUnit.apply(transactionId, finalItems);
            log.info("Merged {} items into one on transaction {}", mergeIds.size(), transactionId);
            return loadWithChildren(transactionId);
        } catch (Exception ex) {
            transactionRepository.markPatchFailed(transactionId);
            log.error("Failed to merge items on transaction {}: {}", transactionId, ex.getMessage(), ex);
            throw new ReceiptProcessingException("Failed to merge items for transaction: " + transactionId, ex);
        }
    }

    /**
     * Deletes the given item ID and inserts the provided replacement items.
     * Reconciliation is validated against the final item list before claiming the lock.
     */
    public Transaction splitItem(String transactionId, String splitId, List<SplitItemEntry> splitItems) {
        Transaction transaction = loadWithChildren(transactionId);
        Map<String, LineItem> existingById = indexById(transaction);

        if (!existingById.containsKey(splitId)) {
            throw new IllegalArgumentException("Line item not found: " + splitId);
        }

        List<LineItem> finalItems = new ArrayList<>();
        for (LineItem existing : transaction.getLineItems()) {
            if (!existing.getId().equals(splitId)) finalItems.add(copyLineItem(existing));
        }
        for (SplitItemEntry entry : splitItems) {
            LineItem newItem = new LineItem();
            newItem.setId(UUID.randomUUID().toString());
            newItem.setDescription(entry.description());
            newItem.setAmount(entry.amount());
            newItem.setQuantity(entry.quantity() != null ? entry.quantity() : 1);
            finalItems.add(newItem);
        }

        validateReconciliation(transactionId, finalItems, transaction);
        claimPatch(transactionId);

        try {
            patchUnit.apply(transactionId, finalItems);
            log.info("Split item {} into {} items on transaction {}", splitId, splitItems.size(), transactionId);
            return loadWithChildren(transactionId);
        } catch (Exception ex) {
            transactionRepository.markPatchFailed(transactionId);
            log.error("Failed to split item on transaction {}: {}", transactionId, ex.getMessage(), ex);
            throw new ReceiptProcessingException("Failed to split item for transaction: " + transactionId, ex);
        }
    }

    public record EditItemRequest(String id, String description, BigDecimal amount, Integer quantity) {}
    public record SplitItemEntry(String description, BigDecimal amount, Integer quantity) {}

    private void claimPatch(String transactionId) {
        if (!transactionRepository.claimForPatching(transactionId)) {
            throw new PatchInProgressException(transactionId);
        }
    }

    private void validateReconciliation(String transactionId, List<LineItem> finalItems, Transaction transaction) {
        BigDecimal itemSum = finalItems.stream()
                .map(LineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = itemSum.add(transaction.sumTaxes());
        if (transaction.getTotal() == null || actual.compareTo(transaction.getTotal()) != 0) {
            throw new TransactionReconciliationException(transactionId, transaction.getTotal(), actual);
        }
    }

    private Map<String, LineItem> indexById(Transaction transaction) {
        Map<String, LineItem> map = new HashMap<>();
        for (LineItem li : transaction.getLineItems()) map.put(li.getId(), li);
        return map;
    }

    private LineItem copyLineItem(LineItem src) {
        LineItem copy = new LineItem();
        copy.setId(src.getId());
        copy.setDescription(src.getDescription());
        copy.setAmount(src.getAmount());
        copy.setQuantity(src.getQuantity());
        return copy;
    }

    private Transaction loadWithChildren(String transactionId) {
        return transactionRepository.findByIdWithChildren(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

}
