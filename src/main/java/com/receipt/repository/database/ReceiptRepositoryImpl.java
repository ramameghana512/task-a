package com.receipt.repository.database;

import com.receipt.model.Receipt;
import com.receipt.repository.ReceiptRepository;
import com.receipt.repository.database.jpa.ReceiptJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "sqlite", matchIfMissing = true)
public class ReceiptRepositoryImpl implements ReceiptRepository {

    private final ReceiptJpaRepository receiptJpaRepository;

    public ReceiptRepositoryImpl(ReceiptJpaRepository receiptJpaRepository) {
        this.receiptJpaRepository = receiptJpaRepository;
    }

    @Override
    public Receipt save(Receipt receipt) {
        return receiptJpaRepository.save(receipt);
    }

    @Override
    public Optional<Receipt> findById(String id) {
        return receiptJpaRepository.findById(id);
    }
}
