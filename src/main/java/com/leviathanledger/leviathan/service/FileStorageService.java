package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * LexTracker Integrity Engine: File Storage Service
 * Purpose: Hardened storage logic that ensures every file is fingerprinted
 * before it hits the disk, satisfying the "Chain of Custody" requirement.
 */
@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    // Storage directories
    private final Path evidenceRoot = Paths.get("uploads/evidence");
    private final Path generatedRoot = Paths.get("uploads/generated");

    public FileStorageService() {
        // Create directories on startup
        try {
            if (!Files.exists(evidenceRoot)) {
                Files.createDirectories(evidenceRoot);
                logger.info("Created evidence directory: {}", evidenceRoot.toAbsolutePath());
            }
            if (!Files.exists(generatedRoot)) {
                Files.createDirectories(generatedRoot);
                logger.info("Created generated documents directory: {}", generatedRoot.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create storage directories", e);
        }
    }

    /**
     * Process evidence file upload (for Clerk/Lawyer)
     */
    public FileUploadResult processAndSave(MultipartFile file) {
        try {
            // 1. ENSURE DIRECTORY EXISTS
            if (!Files.exists(evidenceRoot)) {
                Files.createDirectories(evidenceRoot);
            }

            // 2. GENERATE INTEGRITY HASH (The Digital Fingerprint)
            byte[] fileBytes = file.getBytes();
            String fileHash = HashUtils.generateSHA256(fileBytes);

            // 3. GENERATE COLLISION-PROOF FILENAME
            String originalFileName = file.getOriginalFilename();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueFileName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + originalFileName;
            Path destinationFile = evidenceRoot.resolve(uniqueFileName);

            // 4. PHYSICAL STORAGE
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Evidence saved: {} (Size: {} bytes, Hash: {})",
                    uniqueFileName, fileBytes.length, fileHash.substring(0, 16));

            // 5. RETURN WHOLESOME METADATA
            return new FileUploadResult(
                    uniqueFileName,
                    destinationFile.toString(),
                    fileHash,
                    file.getContentType(),
                    fileBytes.length
            );

        } catch (IOException e) {
            logger.error("Evidence storage failure", e);
            throw new RuntimeException("LexTracker Storage Failure: Could not encrypt or save file.", e);
        }
    }

    /**
     * Save a generated PDF document (from the PDF Generator)
     */
    public FileUploadResult saveGeneratedDocument(byte[] pdfContent, String documentType, String caseNumber, String uploadedBy) {
        try {
            // 1. ENSURE DIRECTORY EXISTS
            if (!Files.exists(generatedRoot)) {
                Files.createDirectories(generatedRoot);
            }

            // 2. GENERATE INTEGRITY HASH
            String fileHash = HashUtils.generateSHA256(pdfContent);

            // 3. GENERATE MEANINGFUL FILENAME
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("%s_%s_%s.pdf",
                    documentType.replace("_", ""),
                    caseNumber,
                    timestamp);

            Path destinationFile = generatedRoot.resolve(fileName);

            // 4. PHYSICAL STORAGE
            Files.write(destinationFile, pdfContent);

            logger.info("Generated document saved: {} (Size: {} bytes, Hash: {})",
                    fileName, pdfContent.length, fileHash.substring(0, 16));

            return new FileUploadResult(
                    fileName,
                    destinationFile.toString(),
                    fileHash,
                    "application/pdf",
                    pdfContent.length
            );

        } catch (IOException e) {
            logger.error("Generated document storage failure", e);
            throw new RuntimeException("Failed to save generated document: " + e.getMessage(), e);
        }
    }

    /**
     * Read a file from storage by its path
     */
    public byte[] readFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        return Files.readAllBytes(path);
    }

    /**
     * Delete a file from storage
     */
    public boolean deleteFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            Files.delete(path);
            logger.info("Deleted file: {}", filePath);
            return true;
        }
        return false;
    }

    /**
     * Calculate hash for a MultipartFile (convenience method)
     */
    public String calculateHash(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            return HashUtils.generateSHA256(fileBytes);
        } catch (IOException e) {
            logger.error("Failed to calculate hash", e);
            return "";
        }
    }

    /**
     * Calculate hash for byte array
     */
    public String calculateHash(byte[] data) {
        return HashUtils.generateSHA256(data);
    }

    /**
     * FileUploadResult: A "Data Carrier" record.
     * This makes it easy for the DocumentService to get all info needed for the Database "Vault".
     */
    public record FileUploadResult(
            String fileName,
            String filePath,
            String fileHash,
            String contentType,
            long fileSize
    ) {
        // Constructor for backward compatibility with existing code
        public FileUploadResult(String fileName, String filePath, String fileHash, String contentType) {
            this(fileName, filePath, fileHash, contentType, 0);
        }
    }
}