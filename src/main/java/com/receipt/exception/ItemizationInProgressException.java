package com.receipt.exception;

public class ItemizationInProgressException extends RuntimeException {

    public ItemizationInProgressException(String transactionId) {
        super("Transaction " + transactionId + " is currently being itemized by another request.");
    }
}
