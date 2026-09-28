package com.receipt.service;

import com.receipt.model.Receipt;
import com.receipt.enums.ReceiptStatus;
import com.receipt.repository.ReceiptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class ReceiptStorageService {

    private final ReceiptRepository receiptRepository;
    private final Path uploadDir;

    public ReceiptStorageService(
            ReceiptRepository receiptRepository,
            @Value("${app.upload-dir}") String uploadDir
    ) {
        this.receiptRepository = receiptRepository;
        this.uploadDir = Path.of(uploadDir);
    }

    /**
     * Stores an uploaded receipt file on disk and records its metadata.
     * Only plain-text (.txt) files are accepted at this stage.
     *
     * @throws IllegalArgumentException  if the file is null or empty
     * @throws ResponseStatusException   (415) if the file type is not supported
     * @throws IllegalStateException     if the file cannot be written to disk
     */
    public Receipt upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Receipt file must not be empty.");
        }
        validateReceiptFile(file);

        try {
            Files.createDirectories(uploadDir);

            String receiptId = newReceiptId();
            String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "receipt");
            String storedFilename = receiptId + "-" + Path.of(originalFilename).getFileName();
            Path storedPath = uploadDir.resolve(storedFilename);

            Files.copy(file.getInputStream(), storedPath);

            Receipt receipt = new Receipt();
            receipt.setId(receiptId);
            receipt.setOriginalFilename(originalFilename);
            receipt.setFilePath(storedPath.toString());
            receipt.setFileSize(file.getSize());
            receipt.setContentType(file.getContentType());
            receipt.setStatus(ReceiptStatus.UPLOADED);

            Receipt saved = receiptRepository.save(receipt);
            log.info("Uploaded receipt {} from file {}", saved.getId(), originalFilename);
            return saved;
        } catch (IOException ex) {
            log.error("Failed to store uploaded receipt file {}", file.getOriginalFilename(), ex);
            throw new IllegalStateException("Failed to store uploaded receipt file.", ex);
        }
    }

    private void validateReceiptFile(MultipartFile file) {
        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        if (!originalFilename.toLowerCase().endsWith(".txt")) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only .txt receipt files are supported for now."
            );
        }
    }

    private String newReceiptId() {
        return UUID.randomUUID().toString();
    }
}
