package com.receipt.enums;

/**
 * Result of reconciling line items + taxes against the transaction total.
 * COMPLETE      -> items + taxes == total (fully itemized).
 * NEEDS_REVIEW  -> mismatch, or no reliable items were extracted.
 * FAILED        -> extraction could not produce a usable transaction at all.
 * ITEMIZING     -> auto-itemization is currently in progress (claimed lock);
 *                  blocks concurrent itemize/patch attempts until it resolves.
 * PATCHING      -> a manual edit/merge/split is currently in progress (claimed lock);
 *                  blocks concurrent auto-itemize and other patch attempts until it resolves.
 */
public enum ItemizeStatus {
    COMPLETE,
    NEEDS_REVIEW,
    FAILED,
    ITEMIZING,
    PATCHING
}
