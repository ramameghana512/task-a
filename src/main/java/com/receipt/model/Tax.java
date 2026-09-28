package com.receipt.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single tax line extracted from the receipt (e.g. VAT, GST, CGST/SGST).
 * Immutable once created by /process — never edited via the items PATCH
 * endpoint, since it reflects what was actually charged on the receipt.
 */
@Entity
@Table(name = "taxes")
@Getter
@Setter
@NoArgsConstructor
public class Tax {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    @JsonIgnore
    private Transaction transaction;

    /** e.g. "VAT", "GST", "CGST" etc..*/
    private String name;

    /** Percentage rate, e.g. 0.19 for 19%. */
    private BigDecimal rate;

    private BigDecimal amount;

    /** Optional: which authority/region this tax is paid to (e.g. "Germany", "Karnataka"). */
    private String jurisdiction;

    private LocalDateTime createdAt = LocalDateTime.now();
}
