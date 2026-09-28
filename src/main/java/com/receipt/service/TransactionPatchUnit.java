package com.receipt.service;

import com.receipt.enums.ItemizeStatus;
import com.receipt.exception.TransactionNotFoundException;
import com.receipt.model.LineItem;
import com.receipt.model.Transaction;
import com.receipt.repository.LineItemRepository;
import com.receipt.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Executes the atomic unit of patch work (edit / merge / split).
 * Lives in a separate bean so @Transactional is honoured through Spring's AOP proxy.
 *
 * For PATCH (edit): Updates existing items in-place, keeps original IDs.
 * For operations with new/deleted items (merge/split): Deletes old items and inserts new ones.
 * Sets itemize_status = COMPLETE — all in one DB transaction.
 * If anything throws, Spring rolls back; the caller marks itemize_status = FAILED separately.
 */
@Service
public class TransactionPatchUnit {

    private final TransactionRepository transactionRepository;
    private final LineItemRepository lineItemRepository;

    public TransactionPatchUnit(TransactionRepository transactionRepository,
                                LineItemRepository lineItemRepository) {
        this.transactionRepository = transactionRepository;
        this.lineItemRepository = lineItemRepository;
    }

    @Transactional
    public Transaction apply(String transactionId, List<LineItem> finalItems) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        List<LineItem> existing = lineItemRepository.findByTransactionId(transactionId);
        boolean hasNewOrDeletedItems = hasNewOrDeletedItems(existing, finalItems);

        if (hasNewOrDeletedItems) {
            lineItemRepository.deleteByTransactionId(transactionId);
            for (LineItem item : finalItems) {
                item.setTransaction(tx);
            }
            lineItemRepository.saveAll(finalItems);
        } else {
            for (LineItem item : finalItems) {
                item.setTransaction(tx);
            }
            lineItemRepository.updateAll(finalItems);
        }

        transactionRepository.setItemizeStatus(transactionId, ItemizeStatus.COMPLETE.name());

        return transactionRepository.findByIdWithChildren(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private boolean hasNewOrDeletedItems(List<LineItem> existing, List<LineItem> finalItems) {
        return existing.size() != finalItems.size();
    }
}
