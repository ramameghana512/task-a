package com.receipt.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single auto-itemized (or user-edited) line from the receipt.
 * Only "amount" (and quantity/description) are user-editable via
 * PATCH /transactions/{id}/items; tax is intentionally NOT tracked per
 * item here to avoid a stale/derived field that would need to be kept in
 * sync on every edit - reconciliation only ever uses Transaction-level
 * taxes (see Transaction#reconciles).
 */
@Entity
@Table(name = "line_items")
@Getter
@Setter
@NoArgsConstructor
public class LineItem {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    @JsonIgnore
    private Transaction transaction;

    private String description;

    private BigDecimal amount;

    private int quantity = 1;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
