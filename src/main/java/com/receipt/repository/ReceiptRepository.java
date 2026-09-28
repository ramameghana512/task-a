package com.receipt.repository;

import com.receipt.model.Receipt;

import java.util.Optional;

/**
 * Storage contract for receipt persistence.
 * Receipts track the file upload lifecycle only (UPLOADED | FAILED).
 * Processing pipeline state lives on the Transaction.
 */
public interface ReceiptRepository {

    Receipt save(Receipt receipt);

    Optional<Receipt> findById(String id);
}
