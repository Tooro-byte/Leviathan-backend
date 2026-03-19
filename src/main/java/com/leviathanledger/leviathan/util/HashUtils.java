package com.leviathanledger.leviathan.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * LexTracker Integrity Engine: Hashing Utility
 * Purpose: Generates immutable SHA-256 digital fingerprints for all legal evidence.
 * Tagline: "If one bit changes, the fingerprint breaks."
 */
public class HashUtils {

    /**
     * Generates a SHA-256 hex string from the byte content of a file.
     * * @param fileBytes The raw bytes of the uploaded document.
     * @return A 64-character hexadecimal string representing the unique file fingerprint.
     */
    public static String generateSHA256(byte[] fileBytes) {
        if (fileBytes == null) {
            throw new IllegalArgumentException("File content cannot be null for hashing.");
        }

        try {
            // Initialize the SHA-256 MessageDigest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Perform the hashing math
            byte[] encodedHash = digest.digest(fileBytes);

            // Convert the resulting byte array into a human-readable Hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                // Convert each byte to a hex string and ensure 2-digit padding
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // This exception occurs if the JVM doesn't support SHA-256 (standard in Java 21)
            throw new RuntimeException("LexTracker Error: SHA-256 algorithm not found in the environment.", e);
        }
    }

    /**
     * Optional: Validates if a file matches its stored fingerprint.
     * * @param fileBytes The bytes of the file currently on disk.
     * @param storedHash The hash retrieved from the MySQL "Vault".
     * @return True if the file is untampered.
     */
    public static boolean verifyIntegrity(byte[] fileBytes, String storedHash) {
        String currentHash = generateSHA256(fileBytes);
        return currentHash.equalsIgnoreCase(storedHash);
    }
}