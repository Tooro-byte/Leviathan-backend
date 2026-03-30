package com.leviathanledger.leviathan.util;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Leviathan Ledger: Integrity Engine Utility
 * Responsible for generating immutable SHA-256 fingerprints.
 */
public class FileHasher {

    /**
     * Generates a hash from a new upload (MultipartFile)
     */
    public static String generateSHA256(MultipartFile file) throws IOException, NoSuchAlgorithmException {
        return calculateHashFromBytes(file.getBytes());
    }

    /**
     * Generates a hash from a file already on the disk (Path)
     * Used by DocumentService.verifyDocumentIntegrity()
     */
    public static String calculateHashFromPath(Path filePath) throws IOException, NoSuchAlgorithmException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        return calculateHashFromBytes(fileBytes);
    }

    /**
     * Core hashing logic used by both entry points to ensure consistency
     */
    private static String calculateHashFromBytes(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(bytes);

        StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
        for (byte b : encodedHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}