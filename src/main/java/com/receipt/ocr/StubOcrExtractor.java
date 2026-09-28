package com.receipt.ocr;

import com.receipt.exception.ReceiptProcessingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stub OCR: reads the uploaded file as plain UTF-8 text.
 * Used for fixture-based testing where the "receipt" is already a .txt file.
 * Active when app.ocr.strategy=stub (the default).
 */
@Component
@ConditionalOnProperty(prefix = "app.ocr", name = "strategy", havingValue = "stub", matchIfMissing = true)
public class StubOcrExtractor implements OcrExtractor {

    @Override
    public String extract(String filePath) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ReceiptProcessingException("Failed to read receipt file: " + filePath, ex);
        }
    }
}
