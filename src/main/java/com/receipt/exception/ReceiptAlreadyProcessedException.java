package com.receipt.exception;

import com.receipt.enums.ProcessingStatus;

public class ReceiptAlreadyProcessedException extends RuntimeException {

    private final ProcessingStatus currentStatus;
    private final String transactionId;

    public ReceiptAlreadyProcessedException(String receiptId, ProcessingStatus currentStatus, String transactionId) {
        super(buildMessage(receiptId, currentStatus));
        this.currentStatus = currentStatus;
        this.transactionId = transactionId;
    }

    private static String buildMessage(String receiptId, ProcessingStatus status) {
        return switch (status) {
            case PROCESSING -> "Receipt " + receiptId + " is currently being processed by another request.";
            case PROCESSED  -> "Receipt " + receiptId + " has already been processed.";
            case FAILED     -> "Receipt " + receiptId + " previously failed to process. Call /process again to retry.";
        };
    }

    public ProcessingStatus getCurrentStatus() { return currentStatus; }
    public String getTransactionId() { return transactionId; }
}
