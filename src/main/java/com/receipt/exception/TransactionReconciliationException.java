package com.receipt.exception;

import java.math.BigDecimal;

public class TransactionReconciliationException extends RuntimeException {

    public TransactionReconciliationException(String transactionId, BigDecimal expected, BigDecimal actual) {
        super("Transaction " + transactionId + " does not reconcile. Expected " + expected + " but got " + actual + ".");
    }
}
