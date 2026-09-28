package com.receipt.repository.database;

import com.receipt.model.Transaction;
import com.receipt.repository.TransactionRepository;
import com.receipt.repository.database.jpa.TransactionJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "sqlite", matchIfMissing = true)
public class TransactionRepositoryImpl implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    public TransactionRepositoryImpl(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public void save(Transaction transaction) {
        transactionJpaRepository.save(transaction);
    }

    @Override
    public Optional<Transaction> findById(String id) {
        return transactionJpaRepository.findById(id);
    }

    @Override
    public Optional<Transaction> findByIdWithChildren(String id) {
        return transactionJpaRepository.findByIdWithChildren(id);
    }

    @Override
    public Optional<Transaction> findByReceiptId(String receiptId) {
        return transactionJpaRepository.findByReceiptId(receiptId);
    }

    @Override
    public Optional<Transaction> findByReceiptIdWithChildren(String receiptId) {
        return transactionJpaRepository.findByReceiptIdWithChildren(receiptId);
    }

    @Override
    public boolean claimForRetry(String receiptId, String rawOcrText) {
        return transactionJpaRepository.claimForRetry(receiptId, rawOcrText) == 1;
    }

    @Override
    public void markProcessingFailed(String id) {
        transactionJpaRepository.markProcessingFailed(id);
    }

    @Override
    public boolean claimForItemizing(String id) {
        return transactionJpaRepository.claimForItemizing(id) == 1;
    }

    @Override
    public void markItemizeFailed(String id) {
        transactionJpaRepository.markItemizeFailed(id);
    }

    @Override
    public boolean claimForPatching(String id) {
        return transactionJpaRepository.claimForPatching(id) == 1;
    }

    @Override
    public void markPatchFailed(String id) {
        transactionJpaRepository.markPatchFailed(id);
    }

    @Override
    public void setItemizeStatus(String id, String status) {
        transactionJpaRepository.setItemizeStatus(id, status);
    }
}
