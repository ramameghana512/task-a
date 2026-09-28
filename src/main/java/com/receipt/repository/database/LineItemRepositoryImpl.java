package com.receipt.repository.database;

import com.receipt.model.LineItem;
import com.receipt.repository.LineItemRepository;
import com.receipt.repository.database.jpa.LineItemJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "sqlite", matchIfMissing = true)
public class LineItemRepositoryImpl implements LineItemRepository {

    private final LineItemJpaRepository lineItemJpaRepository;

    public LineItemRepositoryImpl(LineItemJpaRepository lineItemJpaRepository) {
        this.lineItemJpaRepository = lineItemJpaRepository;
    }

    @Override
    public List<LineItem> saveAll(List<LineItem> items) {
        return lineItemJpaRepository.saveAll(items);
    }

    @Override
    public List<LineItem> findByTransactionId(String transactionId) {
        return lineItemJpaRepository.findByTransaction_Id(transactionId);
    }

    @Override
    @Transactional
    public void deleteByTransactionId(String transactionId) {
        lineItemJpaRepository.bulkDeleteByTransactionId(transactionId);
    }

    @Override
    @Transactional
    public void updateAll(List<LineItem> items) {
        for (LineItem item : items) {
            item.setUpdatedAt(LocalDateTime.now());
            lineItemJpaRepository.save(item);
        }
    }
}
