package com.receipt.repository.database;

import com.receipt.model.Tax;
import com.receipt.repository.TaxRepository;
import com.receipt.repository.database.jpa.TaxJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "sqlite", matchIfMissing = true)
public class TaxRepositoryImpl implements TaxRepository {

    private final TaxJpaRepository taxJpaRepository;

    public TaxRepositoryImpl(TaxJpaRepository taxJpaRepository) {
        this.taxJpaRepository = taxJpaRepository;
    }

    @Override
    public List<Tax> saveAll(List<Tax> taxes) {
        return taxJpaRepository.saveAll(taxes);
    }

    @Override
    public List<Tax> findByTransactionId(String transactionId) {
        return taxJpaRepository.findByTransaction_Id(transactionId);
    }

    @Override
    @Transactional
    public void deleteByTransactionId(String transactionId) {
        taxJpaRepository.deleteByTransaction_Id(transactionId);
    }
}
