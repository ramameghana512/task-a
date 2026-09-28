package com.receipt.repository;

import com.receipt.model.LineItem;

import java.util.List;

/**
 * Storage contract for line item persistence.
 */
public interface LineItemRepository {

    List<LineItem> saveAll(List<LineItem> items);

    List<LineItem> findByTransactionId(String transactionId);

    void deleteByTransactionId(String transactionId);

    void updateAll(List<LineItem> items);
}
