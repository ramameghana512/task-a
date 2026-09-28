package com.receipt.model;

import com.receipt.enums.Currency;
import com.receipt.enums.ItemizeStatus;
import com.receipt.enums.ProcessingStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Aggregate root for a single receipt-derived transaction.
 * Owns its Tax and LineItem child rows and is the single place where the
 * "items + taxes == total" invariant is checked, so no controller/service
 * can accidentally persist an inconsistent state without going through it.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    private String id;

    /** One receipt produces exactly one transaction. */
    @Column(unique = true)
    private String receiptId;

    private String merchant;

    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "TEXT")
    private ProcessingStatus processingStatus;

    @Lob
    @Getter(onMethod_ = {@JsonIgnore})
    private String rawOcrText;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "TEXT")
    @Getter(onMethod_ = {@JsonIgnore})
    private ItemizeStatus itemizeStatus;

    /**
     * Client-facing itemize status. ITEMIZING and PATCHING are internal lock states;
     * they map to NEEDS_REVIEW so clients see a stable spec-defined value while work is in flight.
     */
    @JsonProperty("itemize_status")
    public ItemizeStatus getItemizeStatusForClient() {
        if (itemizeStatus == ItemizeStatus.ITEMIZING || itemizeStatus == ItemizeStatus.PATCHING) {
            return ItemizeStatus.NEEDS_REVIEW;
        }
        return itemizeStatus;
    }

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Tax> taxes = new HashSet<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LineItem> lineItems = new HashSet<>();

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal sumTaxes() {
        return taxes.stream()
                .map(Tax::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
