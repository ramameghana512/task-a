package com.receipt.parser;

import com.receipt.enums.Currency;
import com.receipt.enums.ItemizeStatus;
import com.receipt.model.LineItem;
import com.receipt.model.Tax;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
public class ParsedDocument {

    private final String merchant;
    private final LocalDate transactionDate;
    private final Currency currency;
    private final BigDecimal total;
    private final List<Tax> taxes;
    private final List<LineItem> lineItems;
    private final ItemizeStatus itemizeStatus;

    public ParsedDocument(
            String merchant,
            LocalDate transactionDate,
            Currency currency,
            BigDecimal total,
            List<Tax> taxes,
            List<LineItem> lineItems,
            ItemizeStatus itemizeStatus
    ) {
        this.merchant = merchant;
        this.transactionDate = transactionDate;
        this.currency = currency;
        this.total = total;
        this.taxes = taxes;
        this.lineItems = lineItems;
        this.itemizeStatus = itemizeStatus;
    }

}
