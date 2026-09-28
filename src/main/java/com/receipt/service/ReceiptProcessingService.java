package com.receipt.service;

import com.receipt.enums.ProcessingStatus;
import com.receipt.enums.ReceiptStatus;
import com.receipt.exception.ReceiptAlreadyProcessedException;
import com.receipt.exception.ReceiptNotFoundException;
import com.receipt.exception.ReceiptProcessingException;
import com.receipt.model.Receipt;
import com.receipt.model.Transaction;
import com.receipt.ocr.OcrExtractor;
import com.receipt.parser.ParsedDocument;
import com.receipt.parser.ReceiptTextParser;
import com.receipt.repository.ReceiptRepository;
import com.receipt.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class ReceiptProcessingService {

    private final ReceiptRepository receiptRepository;
    private final TransactionRepository transactionRepository;
    private final OcrExtractor ocrExtractor;
    private final ReceiptTextParser receiptTextParser;
    private final ReceiptProcessingUnit processingUnit;

    public ReceiptProcessingService(
            ReceiptRepository receiptRepository,
            TransactionRepository transactionRepository,
            OcrExtractor ocrExtractor,
            ReceiptTextParser receiptTextParser,
            ReceiptProcessingUnit processingUnit
    ) {
        this.receiptRepository = receiptRepository;
        this.transactionRepository = transactionRepository;
        this.ocrExtractor = ocrExtractor;
        this.receiptTextParser = receiptTextParser;
        this.processingUnit = processingUnit;
    }

    /**
     * Reads the uploaded receipt file, parses its OCR text, and persists the result
     * as a Transaction with taxes and line items.
     *
     * No outer @Transactional — each step auto-commits independently:
     *   Tx-Claim: INSERT stub (first attempt) or UPDATE WHERE FAILED (retry) auto-commits.
     *   Tx-Work:  @Transactional saveAndComplete fills the stub + marks PROCESSED atomically.
     *   Tx-Fail:  markProcessingFailed auto-commits in catch, survives Tx-Work rollback.
     */
    public Transaction processReceipt(String receiptId) {
        log.info("Processing receipt {}", receiptId);

        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ReceiptNotFoundException(receiptId));

        if (receipt.getStatus() == ReceiptStatus.FAILED) {
            throw new ReceiptProcessingException("Receipt upload failed, cannot process: " + receiptId);
        }

        String rawOcrText = ocrExtractor.extract(receipt.getFilePath());

        String transactionId = claimForProcessing(receiptId, rawOcrText);

        ParsedDocument parsed = receiptTextParser.parse(rawOcrText);

        try {
            Transaction transaction = processingUnit.saveAndComplete(transactionId, parsed);
            log.info("Processed receipt {} into transaction {} with {} taxes and {} line items",
                    receiptId, transaction.getId(),
                    transaction.getTaxes().size(), transaction.getLineItems().size());
            return transaction;
        } catch (Exception ex) {
            transactionRepository.markProcessingFailed(transactionId);
            log.error("Failed to process receipt {}, marked transaction {} as FAILED: {}",
                    receiptId, transactionId, ex.getMessage(), ex);
            throw new ReceiptProcessingException("Failed to process receipt: " + receiptId, ex);
        }
    }

    /**
     * Returns the transactionId to use for Tx-Work, or throws 409 if blocked.
     *
     * First attempt:  INSERT stub row (unique constraint on receipt_id = one thread wins).
     *                 DataIntegrityViolationException means another thread beat us — re-read.
     * Retry (FAILED): UPDATE WHERE processing_status = 'FAILED' — only one thread wins.
     */
    private String claimForProcessing(String receiptId, String rawOcrText) {
        Optional<Transaction> existing = transactionRepository.findByReceiptId(receiptId);

        if (existing.isEmpty()) {
            try {
                Transaction stub = buildStub(receiptId, rawOcrText);
                transactionRepository.save(stub);
                return stub.getId();
            } catch (DataIntegrityViolationException e) {
                existing = transactionRepository.findByReceiptId(receiptId);
                if (existing.isEmpty()) {
                    throw new ReceiptProcessingException(
                            "Unexpected state after constraint violation for receipt: " + receiptId);
                }
            }
        }

        Transaction tx = existing.get();
        return switch (tx.getProcessingStatus()) {
            case PROCESSING -> throw new ReceiptAlreadyProcessedException(
                    receiptId, ProcessingStatus.PROCESSING, null);
            case PROCESSED  -> throw new ReceiptAlreadyProcessedException(
                    receiptId, ProcessingStatus.PROCESSED, tx.getId());
            case FAILED -> {
                boolean claimed = transactionRepository.claimForRetry(receiptId, rawOcrText);
                if (claimed) yield tx.getId();
                throw new ReceiptAlreadyProcessedException(
                        receiptId, ProcessingStatus.PROCESSING, null);
            }
        };
    }

    private Transaction buildStub(String receiptId, String rawOcrText) {
        Transaction stub = new Transaction();
        stub.setId(UUID.randomUUID().toString());
        stub.setReceiptId(receiptId);
        stub.setRawOcrText(rawOcrText);
        stub.setProcessingStatus(ProcessingStatus.PROCESSING);
        return stub;
    }

}
