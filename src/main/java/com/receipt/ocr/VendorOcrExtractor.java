package com.receipt.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vendor OCR: sends the receipt file to a real OCR/VLM API and returns the extracted text.
 * Active when app.ocr.strategy=vendor.
 *
 * To implement:
 *   1. Inject your vendor client (e.g. OpenAI, Google Vision, AWS Textract).
 *   2. Read the file bytes from filePath.
 *   3. Call the vendor API and return the raw text response.
 *   4. Set the API key via environment variable (never hardcode it).
 */
@Component
@ConditionalOnProperty(prefix = "app.ocr", name = "strategy", havingValue = "vendor")
public class VendorOcrExtractor implements OcrExtractor {

    @Override
    public String extract(String filePath) {
        throw new UnsupportedOperationException(
                "Vendor OCR not implemented. Set app.ocr.strategy=stub to use fixture-based parsing.");
    }
}
