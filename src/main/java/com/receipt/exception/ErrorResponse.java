package com.receipt.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body for all API error responses.
 * Fields that are null are omitted from the JSON output (e.g. transactionId
 * is only present on the "receipt already processed" 409).
 *
 * <pre>
 * {
 *   "status": 409,
 *   "title":  "Receipt Already Processed",
 *   "detail": "Receipt abc-123 has already been processed.",
 *   "transactionId": "def-456"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String title,
        String detail,
        String transactionId
) {
    /** Convenience factory for errors without a transactionId. */
    public static ErrorResponse of(int status, String title, String detail) {
        return new ErrorResponse(status, title, detail, null);
    }
}
