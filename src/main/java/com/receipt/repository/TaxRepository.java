package com.receipt.repository;

import com.receipt.model.Tax;

import java.util.List;

/**
 * Storage contract for tax persistence.
 */
public interface TaxRepository {

    List<Tax> saveAll(List<Tax> taxes);

    List<Tax> findByTransactionId(String transactionId);

    void deleteByTransactionId(String transactionId);
}
