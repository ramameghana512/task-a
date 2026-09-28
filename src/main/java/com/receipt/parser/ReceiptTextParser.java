package com.receipt.parser;

import com.receipt.enums.Currency;
import com.receipt.enums.ItemizeStatus;
import com.receipt.model.LineItem;
import com.receipt.model.Tax;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw OCR text from a receipt into a {@link ParsedDocument}.
 *
 * <p><b>Supported input format (structured fixture / test receipts):</b>
 * <pre>
 * MERCHANT: Cafe Mitte
 * DATE: 2026-03-12
 * CURRENCY: EUR
 *
 * Espresso                    3.50
 * 2x Sandwich                17.80
 *
 * Subtotal                   21.30
 * VAT 19%                     4.04
 * TOTAL                      25.34
 * </pre>
 *
 * <p><b>parser also handles:</b>
 * <ul>
 *   <li>Multiple date formats: 2026-03-12 / 03/12/2026 / Mar 12 2026 / 12-Mar-26</li>
 *   <li>Currency symbols on amounts: $3.50, €3.50, £2.99, ₹120</li>
 *   <li>Total label variants: "Grand Total", "Amount Due", "Balance Due", "Please Pay"</li>
 *   <li>Tax without a percentage: "GST: 1.50", "Tax $1.50"</li>
 *   <li>Quantity prefixes: "2x Espresso 7.00", "3 @ 2.50 7.50"</li>
 *   <li>Skip lines that look like items but are not: Subtotal, Discount, Tip, Change</li>
 * </ul>
 */
@Component
public class ReceiptTextParser {

    // -------------------------------------------------------------------------
    // Patterns for amount extraction
    // -------------------------------------------------------------------------

    /**
     * Matches any line whose last token is a (possibly negative) decimal number.
     * Capture group 1 = description, group 2 = amount.
     *
     * <pre>
     * "Espresso          3.50"  → desc="Espresso", amount="3.50"
     * "Discount         -2.00"  → desc="Discount", amount="-2.00"
     * </pre>
     */
    private static final Pattern TRAILING_AMOUNT =
            Pattern.compile("^(.*?\\S)\\s+(-?\\d+(?:\\.\\d{1,2})?)$");

    // -------------------------------------------------------------------------
    // Patterns for tax lines
    // -------------------------------------------------------------------------

    /**
     * Primary tax pattern: name + percentage + amount on one line.
     * <pre>
     * "VAT 19% 2.85"          ✓
     * "incl. GST 10% 1.20"    ✓  (leading "incl." is optional)
     * "CGST 9% 0.90"          ✓
     * </pre>
     */
    private static final Pattern TAX_WITH_RATE =
            Pattern.compile(
                    "^(?:incl\\.\\s*)?(.+?)\\s+(\\d+(?:\\.\\d+)?)%\\s+(-?\\d+(?:\\.\\d{1,2})?)$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Fallback tax pattern: just a tax label + amount, no percentage available.
     * <pre>
     * "GST: 1.50"      ✓
     * "Tax $1.50"      ✓  (currency symbol is stripped before matching)
     * "Service Tax 2.00" ✓
     * </pre>
     */
    private static final Pattern TAX_NO_RATE =
            Pattern.compile(
                    "^((?:tax|gst|vat|hst|pst|qst|service\\s+tax|cgst|sgst|igst)(?:[^\\d]*))(\\d+(?:\\.\\d{1,2})?)$",
                    Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Patterns for quantity-annotated item lines
    // -------------------------------------------------------------------------

    /**
     * Quantity prefix style: "2x Espresso 7.00" or "2 x Espresso 7.00"
     * Groups: (1) qty, (2) description, (3) amount.
     */
    private static final Pattern QTY_PREFIX =
            Pattern.compile("^(\\d+)\\s*[xX]\\s+(.+?)\\s+(-?\\d+(?:\\.\\d{1,2})?)$");

    /**
     * Unit-price style: "3 @ 2.50 7.50"
     * Groups: (1) qty, (2) unit price, (3) line total. Description is carried from caller context.
     */
    private static final Pattern QTY_AT_PRICE =
            Pattern.compile("^(\\d+)\\s*@\\s*(\\d+(?:\\.\\d{1,2})?)\\s+(-?\\d+(?:\\.\\d{1,2})?)$");

    // -------------------------------------------------------------------------
    // Patterns for header fields
    // -------------------------------------------------------------------------

    /**
     * Matches any of the common "grand total" labels found on real receipts.
     * <pre>
     * "TOTAL              17.85"        ✓
     * "Grand Total        17.85"        ✓
     * "Amount Due:        17.85"        ✓
     * "BALANCE DUE        17.85"        ✓
     * "Please Pay         17.85"        ✓
     * </pre>
     * NOTE: "SUBTOTAL" is intentionally excluded — it is handled by isSummaryLine().
     */
    private static final Pattern TOTAL_LABEL =
            Pattern.compile(
                    "^(grand\\s+total|total\\s+due|amount\\s+due|balance\\s+due|please\\s+pay|total)\\b.*",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Detects a "MERCHANT:" label line (case-insensitive).
     * Example: "MERCHANT: Cafe Mitte"
     */
    private static final Pattern MERCHANT_LABEL =
            Pattern.compile("^merchant\\s*:", Pattern.CASE_INSENSITIVE);

    /**
     * Detects a "DATE:" label line (case-insensitive).
     * Example: "DATE: 2026-03-12"
     */
    private static final Pattern DATE_LABEL =
            Pattern.compile("^date\\s*:", Pattern.CASE_INSENSITIVE);

    /**
     * Detects a "CURRENCY:" label line (case-insensitive).
     * Example: "CURRENCY: EUR"
     */
    private static final Pattern CURRENCY_LABEL =
            Pattern.compile("^currency\\s*:", Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Currency symbol → ISO code mapping
    // -------------------------------------------------------------------------

    /**
     * Used when no "CURRENCY:" label is present but amounts carry a symbol.
     * <pre>
     * "$3.50" → Currency.USD
     * "€3.50" → Currency.EUR
     * "£2.99" → Currency.GBP
     * "₹120"  → Currency.INR
     * </pre>
     */
    private static final Map<String, Currency> SYMBOL_TO_CODE = Map.of(
            "$",  Currency.USD,
            "€",  Currency.EUR,
            "£",  Currency.GBP,
            "₹",  Currency.INR,
            "¥",  Currency.JPY,
            "₩",  Currency.KRW,
            "Fr", Currency.CHF,
            "A$", Currency.AUD,
            "C$", Currency.CAD
    );

    // -------------------------------------------------------------------------
    // Summary / skip keywords — lines that look like items but must be ignored
    // -------------------------------------------------------------------------

    /**
     * Lines whose description starts with any of these keywords are summary or
     * non-purchase lines and must NOT be treated as purchased items.
     *
     * <pre>
     * "Subtotal     15.00"  → derived sum, not an item
     * "Discount     -2.00"  → deduction
     * "Tip           3.00"  → gratuity, not a product
     * "Change        5.00"  → cash back
     * "Service Charge 1.50" → venue fee, not a product
     * </pre>
     */
    private static final Set<String> SUMMARY_KEYWORDS = Set.of(
            "subtotal", "sub-total", "sub total",
            "discount", "promo", "coupon", "savings", "offer",
            "tip", "gratuity",
            "service charge", "service fee",
            "change", "cash", "card", "payment",
            "total savings", "you saved"
    );

    // -------------------------------------------------------------------------
    // Date format fallback chain
    // -------------------------------------------------------------------------

    /**
     * Tried in order until one succeeds. Real OCR can produce any of these.
     * If none matches, date is left null and itemizeStatus will be NEEDS_REVIEW.
     *
     * <pre>
     * ISO_LOCAL_DATE       → "2026-03-12"
     * MM/dd/yyyy           → "03/12/2026"  (US style)
     * dd/MM/yyyy           → "12/03/2026"  (EU style)
     * dd-MM-yyyy           → "12-03-2026"
     * MMM dd yyyy          → "Mar 12 2026"
     * MMM dd, yyyy         → "Mar 12, 2026"
     * dd MMM yyyy          → "12 Mar 2026"
     * dd-MMM-yy            → "12-Mar-26"
     * </pre>
     */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MMM dd yyyy",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy",  Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yy",    Locale.ENGLISH)
    );

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Parses raw OCR text and returns a {@link ParsedDocument}.
     *
     * <p>The method is deliberately lenient: it never throws on malformed lines.
     * Any line that cannot be classified is silently skipped so that a single
     * bad OCR token does not abort the entire receipt.
     *
     * @param rawText the full OCR text of the receipt
     * @return a parsed document; fields may be null if the receipt lacked them
     */
    public ParsedDocument parse(String rawText) {
        List<String> lines = rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        String merchant = null;
        LocalDate date = null;
        Currency currency = null;
        BigDecimal total = null;
        List<Tax> taxes = new ArrayList<>();
        List<LineItem> items = new ArrayList<>();
        // Collects plain-text lines that have no amount (e.g. "Trip fare").
        // Used as the fallback item description when no line items are found.
        List<String> descriptionOnlyLines = new ArrayList<>();
        // Accumulates negative adjustments (coupon, discount) so they are included
        // in reconciliation even though they are not added to lineItems.
        // Example: "Coupon: WELCOME10  -1.45" → adjustmentTotal = -1.45
        BigDecimal adjustmentTotal = BigDecimal.ZERO;

        for (String line : lines) {
            // Lines starting with "(" are parenthetical notes in the fixture files, e.g.
            // "(No itemized list)" — skip them.
            if (line.startsWith("(")) {
                continue;
            }

            if (MERCHANT_LABEL.matcher(line).find()) {
                merchant = valueAfterColon(line);

            } else if (DATE_LABEL.matcher(line).find()) {
                date = parseDate(valueAfterColon(line));

            } else if (CURRENCY_LABEL.matcher(line).find()) {
                // Currency.fromCode returns null for unrecognised codes — parse continues gracefully.
                currency = Currency.fromCode(valueAfterColon(line));

            } else if (isTotalLine(line)) {
                // Must be checked BEFORE isItemLine because total lines also end in a number.
                total = extractTrailingAmount(line);

            } else if (isSummaryLine(line)) {
                // Subtotal, Discount, Tip, Change, etc. — not added as line items.
                // But if the amount is NEGATIVE (coupon, promo, discount), track it so
                // reconciliation can still reach COMPLETE.
                // Example: "Coupon: WELCOME10  -1.45" → adjustmentTotal = -1.45
                // Positive amounts (Subtotal, Change, Cash) are ignored entirely.
                BigDecimal adj = extractTrailingAmount(line);
                if (adj != null && adj.signum() < 0) {
                    adjustmentTotal = adjustmentTotal.add(adj);
                }

            } else if (isTaxLine(line)) {
                Tax tax = tryParseTax(line);
                if (tax != null) taxes.add(tax);

            } else if (isItemLine(line)) {
                LineItem item = tryParseLineItem(line);
                if (item != null) {
                    items.add(item);
                    // If currency was not declared via label, infer from the first amount symbol found.
                    if (currency == null) {
                        currency = inferCurrencyFromLine(line);
                    }
                }

            } else {
                // Plain text with no amount, not a label or keyword — e.g. "Trip fare".
                // Saved so it can be used as the fallback item description below.
                descriptionOnlyLines.add(line);
            }
        }

        // If no line items were parsed (e.g. taxi / toll receipt with just a total),
        // create a single fallback item. Prefer the first plain-text description line
        // ("Trip fare") over the merchant name so the entry is more meaningful.
        if (items.isEmpty() && total != null) {
            String fallbackDesc = descriptionOnlyLines.isEmpty() ? merchant : descriptionOnlyLines.get(0);
            items.add(buildFallbackItem(fallbackDesc, total));
        }

        ItemizeStatus status = determineStatus(total, taxes, items, adjustmentTotal);
        return new ParsedDocument(merchant, date, currency, total, taxes, items, status);
    }

    // =========================================================================
    // Line classifiers
    // =========================================================================

    /**
     * Returns true if the line represents the grand total of the receipt.
     *
     * <p>Matches: "TOTAL", "Grand Total", "Amount Due", "Balance Due", "Please Pay", "Total Due"
     * Does NOT match "SUBTOTAL" (handled by isSummaryLine).
     */
    private boolean isTotalLine(String line) {
        return TOTAL_LABEL.matcher(line).find();
    }

    /**
     * Returns true if the line is a summary/derived value that should be skipped.
     *
     * <p>Examples: "Subtotal 15.00", "Discount -2.00", "Tip 3.00", "Change 5.00"
     */
    private boolean isSummaryLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return SUMMARY_KEYWORDS.stream().anyMatch(lower::startsWith);
    }

    /**
     * Returns true if the line is a tax entry (either with or without a rate).
     *
     * <p>Examples:
     * <pre>
     * "VAT 19% 2.85"      → TAX_WITH_RATE match
     * "incl. GST 10% 1.20"→ TAX_WITH_RATE match
     * "GST: 1.50"         → TAX_NO_RATE match
     * "Tax $1.50"         → TAX_NO_RATE match (after symbol strip)
     * </pre>
     */
    private boolean isTaxLine(String line) {
        return TAX_WITH_RATE.matcher(line).matches()
                || TAX_NO_RATE.matcher(stripCurrencySymbol(line)).matches();
    }

    /**
     * Returns true if the line looks like a purchased item (description + trailing amount).
     * Only called after summary and tax checks so those lines are already excluded.
     */
    private boolean isItemLine(String line) {
        String stripped = stripCurrencySymbol(line);
        return TRAILING_AMOUNT.matcher(stripped).matches()
                || QTY_PREFIX.matcher(stripped).matches()
                || QTY_AT_PRICE.matcher(stripped).matches();
    }

    // =========================================================================
    // Parsers
    // =========================================================================

    /**
     * Attempts to parse a tax line. Returns null (never throws) on bad input.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try TAX_WITH_RATE first — extracts name, rate, and amount.</li>
     *   <li>Fall back to TAX_NO_RATE — extracts name and amount; rate left null.</li>
     * </ol>
     */
    private Tax tryParseTax(String line) {
        try {
            Matcher m = TAX_WITH_RATE.matcher(line);
            if (m.matches()) {
                Tax tax = new Tax();
                tax.setId(newId());
                tax.setName(m.group(1).trim());
                tax.setRate(new BigDecimal(m.group(2)).movePointLeft(2)); // 19 → 0.19
                tax.setAmount(new BigDecimal(m.group(3)));
                return tax;
            }

            String cleaned = stripCurrencySymbol(line);
            m = TAX_NO_RATE.matcher(cleaned);
            if (m.matches()) {
                Tax tax = new Tax();
                tax.setId(newId());
                tax.setName(m.group(1).trim().replaceAll(":$", ""));
                // rate stays null — we don't know it
                tax.setAmount(new BigDecimal(m.group(2)));
                return tax;
            }
        } catch (Exception ignored) {
            // Malformed OCR — skip silently rather than aborting the whole parse
        }
        return null;
    }

    /**
     * Attempts to parse a line item. Returns null (never throws) on bad input.
     *
     * <p>Tries quantity patterns before the plain trailing-amount pattern:
     * <pre>
     * "2x Espresso   7.00"  → qty=2, desc="Espresso", amount=7.00
     * "3 @ 2.50      7.50"  → qty=3, desc="item", amount=7.50
     * "Sandwich      8.90"  → qty=1, desc="Sandwich", amount=8.90
     * </pre>
     */
    private LineItem tryParseLineItem(String line) {
        try {
            String cleaned = stripCurrencySymbol(line);

            // "2x Espresso 7.00"
            Matcher qtyPrefix = QTY_PREFIX.matcher(cleaned);
            if (qtyPrefix.matches()) {
                return buildItem(qtyPrefix.group(2).trim(),
                        new BigDecimal(qtyPrefix.group(3)),
                        Integer.parseInt(qtyPrefix.group(1)));
            }

            // "3 @ 2.50 7.50"
            Matcher qtyAt = QTY_AT_PRICE.matcher(cleaned);
            if (qtyAt.matches()) {
                return buildItem("item",
                        new BigDecimal(qtyAt.group(3)),
                        Integer.parseInt(qtyAt.group(1)));
            }

            // "Sandwich 8.90"
            Matcher plain = TRAILING_AMOUNT.matcher(cleaned);
            if (plain.matches()) {
                return buildItem(plain.group(1).trim(),
                        new BigDecimal(plain.group(2)),
                        1);
            }
        } catch (Exception ignored) {
            // Malformed OCR — skip silently
        }
        return null;
    }

    /**
     * Tries each date format in {@link #DATE_FORMATS} in order.
     * Returns null rather than throwing if none matches.
     *
     * <pre>
     * "2026-03-12"  → parsed by ISO_LOCAL_DATE
     * "Mar 12 2026" → parsed by "MMM dd yyyy"
     * "garbage"     → returns null (NEEDS_REVIEW status will be set)
     * </pre>
     */
    private LocalDate parseDate(String value) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), fmt);
            } catch (Exception ignored) {
                // try next format
            }
        }
        return null;
    }

    // =========================================================================
    // Status determination
    // =========================================================================

    /**
     * Determines how well the parsed items reconcile against the declared total.
     *
     * <ul>
     *   <li>COMPLETE     — items + adjustments + taxes == total (to the cent)</li>
     *   <li>NEEDS_REVIEW — total found but sums don't match, or no items parsed</li>
     *   <li>FAILED       — total could not be extracted at all</li>
     * </ul>
     *
     * <p>adjustmentTotal carries any negative amounts from coupon/discount lines so that
     * a receipt with a valid discount still reaches COMPLETE:
     * <pre>
     * items=14.45, adjustment=-1.45, tax=1.69, total=14.69
     * → 14.45 + (-1.45) + 1.69 = 14.69  → COMPLETE
     * </pre>
     */
    private ItemizeStatus determineStatus(BigDecimal total, List<Tax> taxes,
                                          List<LineItem> items, BigDecimal adjustmentTotal) {
        if (total == null) {
            return ItemizeStatus.FAILED;
        }
        if (items.isEmpty()) {
            return ItemizeStatus.NEEDS_REVIEW;
        }
        BigDecimal itemsSum = items.stream()
                .map(LineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxesSum = taxes.stream()
                .map(Tax::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemsSum.add(adjustmentTotal).add(taxesSum).compareTo(total) == 0
                ? ItemizeStatus.COMPLETE
                : ItemizeStatus.NEEDS_REVIEW;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Extracts the value after the first colon on a labeled line.
     * "MERCHANT: Cafe Mitte" → "Cafe Mitte"
     */
    private String valueAfterColon(String line) {
        int colon = line.indexOf(':');
        return colon >= 0 ? line.substring(colon + 1).trim() : line.trim();
    }

    /**
     * Extracts the trailing decimal number from any line.
     * Returns null if no number is found (caller should handle gracefully).
     */
    private BigDecimal extractTrailingAmount(String line) {
        Matcher m = Pattern.compile("(-?\\d+(?:\\.\\d{1,2})?)\\s*$").matcher(line);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    /**
     * Removes a leading currency symbol so amount patterns can match cleanly.
     * <pre>
     * "$3.50"  → "3.50"
     * "€ 3.50" → "3.50"
     * "3.50"   → "3.50"  (no-op)
     * </pre>
     */
    private String stripCurrencySymbol(String line) {
        // Replace known multi-char symbols first, then single-char symbols
        for (String sym : List.of("A$", "C$", "Fr", "$", "€", "£", "₹", "¥", "₩")) {
            line = line.replace(sym, "").trim();
        }
        return line;
    }

    /**
     * Scans the raw line for a known currency symbol and returns the matching Currency.
     * Used to infer currency when no "CURRENCY:" label exists.
     * <pre>
     * "Espresso $3.50" → Currency.USD
     * "Kaffee   €2.00" → Currency.EUR
     * "Coffee    2.00" → null (no symbol found)
     * </pre>
     */
    private Currency inferCurrencyFromLine(String line) {
        // Check multi-char symbols first to avoid "$" matching before "A$" or "C$"
        for (Map.Entry<String, Currency> entry : SYMBOL_TO_CODE.entrySet()) {
            if (line.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Builds a fallback single-item when the receipt has a total but no line items.
     * Typical for taxi receipts, toll receipts, etc.
     * <pre>
     * desc="Trip fare",        total=24.00 → item: desc="Trip fare",        amount=24.00
     * desc="Berlin Taxi GmbH", total=24.00 → item: desc="Berlin Taxi GmbH", amount=24.00
     * desc=null,               total=24.00 → item: desc="Receipt item",     amount=24.00
     * </pre>
     */
    private LineItem buildFallbackItem(String desc, BigDecimal total) {
        LineItem item = new LineItem();
        item.setId(newId());
        item.setDescription(desc != null ? desc : "Receipt item");
        item.setAmount(total);
        item.setQuantity(1);
        return item;
    }

    private LineItem buildItem(String description, BigDecimal amount, int quantity) {
        LineItem item = new LineItem();
        item.setId(newId());
        item.setDescription(description);
        item.setAmount(amount);
        item.setQuantity(quantity);
        return item;
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
