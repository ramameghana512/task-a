package com.receipt.repository;

import com.receipt.model.Transaction;

import java.util.Optional;

/**
 * Storage contract for transaction persistence.
 */
public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(String id);

    Optional<Transaction> findByIdWithChildren(String id);

    Optional<Transaction> findByReceiptId(String receiptId);

    Optional<Transaction> findByReceiptIdWithChildren(String receiptId);

    boolean claimForRetry(String receiptId, String rawOcrText);

    void markProcessingFailed(String id);

    boolean claimForItemizing(String id);

    void markItemizeFailed(String id);

    boolean claimForPatching(String id);

    void markPatchFailed(String id);

    void setItemizeStatus(String id, String status);
}
