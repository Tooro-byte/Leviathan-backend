package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * LexTracker Integrity Engine: File Storage Service
 * Purpose: Hardened storage logic that ensures every file is fingerprinted
 * before it hits the disk, satisfying the "Chain of Custody" requirement.
 */
@Service
public class FileStorageService {

    // The root directory for storing legal evidence
    private final Path root = Paths.get("uploads");

    /**
     * The "Atomic Upload" method.
     * Handles hashing, unique naming, and physical storage in one flow.
     * * @param file The file uploaded by a Clerk or Lawyer.
     * @return A Result record containing the new filename, physical path, and SHA-256 hash.
     */
    public FileUploadResult processAndSave(MultipartFile file) {
        try {
            // 1. ENSURE DIRECTORY EXISTS
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            // 2. GENERATE INTEGRITY HASH (The Digital Fingerprint)
            // We use our central HashUtils to ensure the math is consistent across the app
            byte[] fileBytes = file.getBytes();
            String fileHash = HashUtils.generateSHA256(fileBytes);

            // 3. GENERATE COLLISION-PROOF FILENAME
            // UUID is used to prevent different Clerks from overwriting files with the same name
            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
            Path destinationFile = this.root.resolve(uniqueFileName);

            // 4. PHYSICAL STORAGE
            // We use StandardCopyOption to ensure we don't fail if a partial file exists
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            // 5. RETURN WHOLESOME METADATA
            return new FileUploadResult(
                    uniqueFileName,
                    destinationFile.toString(),
                    fileHash,
                    file.getContentType()
            );

        } catch (IOException e) {
            throw new RuntimeException("LexTracker Storage Failure: Could not encrypt or save file.", e);
        }
    }

    public String calculateHash(MultipartFile file) {
        return "";
    }

    /**
     * FileUploadResult: A "Data Carrier" record.
     * This makes it easy for the DocumentService to get all info needed for the Database "Vault".
     */
    public record FileUploadResult(
            String fileName,
            String filePath,
            String fileHash,
            String contentType
    ) {}
}