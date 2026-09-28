package com.receipt.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.receipt.model.Receipt;
import com.receipt.model.Transaction;
import com.receipt.service.ReceiptProcessingService;
import com.receipt.service.ReceiptStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {

    private final ReceiptStorageService receiptStorageService;
    private final ReceiptProcessingService receiptProcessingService;

    public ReceiptController(ReceiptStorageService receiptStorageService, ReceiptProcessingService receiptProcessingService) {
        this.receiptStorageService = receiptStorageService;
        this.receiptProcessingService = receiptProcessingService;
    }

    @PostMapping
    public ResponseEntity<ReceiptUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Receipt receipt = receiptStorageService.upload(file);
        return ResponseEntity.ok(new ReceiptUploadResponse(receipt.getId()));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Transaction> process(@org.springframework.web.bind.annotation.PathVariable("id") String receiptId) {
        return ResponseEntity.ok(receiptProcessingService.processReceipt(receiptId));
    }

    public record ReceiptUploadResponse(@JsonProperty("receipt_id") String receiptId) {}
}
