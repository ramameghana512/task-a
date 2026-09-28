package com.receipt.model;

import com.receipt.enums.ReceiptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Aggregate root representing an uploaded receipt file.
 * Holds the raw OCR text once processed, which is the single source of
 * truth used later for re-itemization (POST /transactions/{id}/itemize).
 */
@Entity
@Table(name = "receipts")
@Getter
@Setter
@NoArgsConstructor
public class Receipt {

    @Id
    private String id;

    private String originalFilename;

    private String filePath;

    private long fileSize;

    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "TEXT")
    private ReceiptStatus status;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
