package com.receipt.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.receipt.model.LineItem;
import com.receipt.model.Transaction;
import com.receipt.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> get(@PathVariable("id") String transactionId) {
        return ResponseEntity.ok(transactionService.getTransaction(transactionId));
    }

    @PostMapping("/{id}/itemize")
    public ResponseEntity<Transaction> itemize(@PathVariable("id") String transactionId) {
        return ResponseEntity.ok(transactionService.reItemize(transactionId));
    }

    @PatchMapping("/{id}/items/edit")
    public ResponseEntity<Transaction> editItems(
            @PathVariable("id") String transactionId,
            @RequestBody EditItemsRequest request
    ) {
        return ResponseEntity.ok(transactionService.editItems(transactionId, request.items()));
    }

    @PatchMapping("/{id}/items/merge")
    public ResponseEntity<Transaction> mergeItems(
            @PathVariable("id") String transactionId,
            @RequestBody MergeItemsRequest request
    ) {
        return ResponseEntity.ok(
                transactionService.mergeItems(transactionId, request.mergeIds(), request.description(), request.amount()));
    }

    @PatchMapping("/{id}/items/split")
    public ResponseEntity<Transaction> splitItem(
            @PathVariable("id") String transactionId,
            @RequestBody SplitItemRequest request
    ) {
        return ResponseEntity.ok(transactionService.splitItem(transactionId, request.splitId(), request.items()));
    }

    public record EditItemsRequest(List<TransactionService.EditItemRequest> items) {}

    public record MergeItemsRequest(
            @JsonProperty("merge_ids") List<String> mergeIds,
            String description,
            BigDecimal amount
    ) {}

    public record SplitItemRequest(
            @JsonProperty("split_id") String splitId,
            List<TransactionService.SplitItemEntry> items
    ) {}
}
