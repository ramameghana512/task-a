package com.receipt.enums;

import com.receipt.model.Receipt;

/**
 * Lifecycle status of an uploaded {@link Receipt}.
 * UPLOADED   -> file stored, not yet processed.
 * PROCESSING -> claimed by a processing thread; parse + save in progress.
 */
public enum ReceiptStatus {
    UPLOADED,
    FAILED
}
