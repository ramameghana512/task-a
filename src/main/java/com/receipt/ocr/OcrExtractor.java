package com.receipt.ocr;

/**
 * Strategy contract for extracting raw text from a receipt file.
 *
 * Implementations:
 *   StubOcrExtractor   — reads the file as plain text (fixtures / testing).
 *   VendorOcrExtractor — sends the file to a real OCR vendor API.
 *
 * Activated via app.ocr.strategy: stub (default) | vendor
 */
public interface OcrExtractor {
    String extract(String filePath);
}
