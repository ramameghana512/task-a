package com.receipt.enums;

/**
 * Tracks the OCR/parsing lifecycle of a receipt as it's turned into a Transaction.
 * PROCESSING -> claimed and currently being parsed into a transaction (in progress).
 * PROCESSED  -> successfully parsed; a Transaction now exists for this receipt.
 * FAILED     -> parsing failed; safe to retry (claimForRetry moves FAILED -> PROCESSING).
 */
public enum ProcessingStatus {
    PROCESSING,
    PROCESSED,
    FAILED
}
