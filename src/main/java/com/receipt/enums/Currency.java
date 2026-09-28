package com.receipt.enums;

/**
 * ISO 4217 currency codes supported by the receipt parser.
 *
 */
public enum Currency {
    USD, EUR, GBP, INR, JPY, KRW, CHF, AUD, CAD;

    /**
     * Looks up a Currency by its ISO code string (case-insensitive).
     * Returns null instead of throwing if the code is unrecognised,
     * so an unknown CURRENCY: label on a receipt doesn't crash the parse.
     *
     * <pre>
     * fromCode("EUR") → Currency.EUR
     * fromCode("eur") → Currency.EUR
     * fromCode("XYZ") → null
     * </pre>
     */
    public static Currency fromCode(String code) {
        if (code == null) return null;
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
