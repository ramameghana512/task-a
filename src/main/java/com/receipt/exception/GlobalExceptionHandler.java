package com.receipt.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReceiptNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReceiptNotFound(ReceiptNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Receipt Not Found", ex.getMessage()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Transaction Not Found", ex.getMessage()));
    }

    /**
     * Returns the existing transactionId (when PROCESSED) so the caller can fetch it
     * directly without an extra lookup.
     */
    @ExceptionHandler(ReceiptAlreadyProcessedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyProcessed(ReceiptAlreadyProcessedException ex) {
        String title = switch (ex.getCurrentStatus()) {
            case PROCESSING -> "Receipt Being Processed";
            case PROCESSED  -> "Receipt Already Processed";
            case FAILED     -> "Receipt Previously Failed";
        };
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, title, ex.getMessage(), ex.getTransactionId()));
    }

    @ExceptionHandler(ItemizationInProgressException.class)
    public ResponseEntity<ErrorResponse> handleItemizationInProgress(ItemizationInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Itemization In Progress", ex.getMessage()));
    }

    @ExceptionHandler(PatchInProgressException.class)
    public ResponseEntity<ErrorResponse> handlePatchInProgress(PatchInProgressException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Patch In Progress", ex.getMessage()));
    }

    @ExceptionHandler(TransactionReconciliationException.class)
    public ResponseEntity<ErrorResponse> handleReconciliation(TransactionReconciliationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "Transaction Reconciliation Failed", ex.getMessage()));
    }

    @ExceptionHandler(ReceiptProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessingFailure(ReceiptProcessingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Receipt Processing Failed", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }
}
