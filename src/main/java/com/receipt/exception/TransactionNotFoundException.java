package com.receipt.exception;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }

    public TransactionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
