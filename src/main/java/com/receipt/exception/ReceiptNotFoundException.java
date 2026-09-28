package com.receipt.exception;

public class ReceiptNotFoundException extends RuntimeException {

    public ReceiptNotFoundException(String receiptId) {
        super("Receipt not found: " + receiptId);
    }
}
