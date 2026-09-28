package com.receipt.exception;

public class PatchInProgressException extends RuntimeException {
    public PatchInProgressException(String transactionId) {
        super("Transaction " + transactionId + " is currently being patched by another request.");
    }
}
