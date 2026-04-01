package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
import com.leviathanledger.leviathan.util.FileHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * LEVIATHAN LEDGER V2.0 - CORE DOCUMENT SERVICE
 * Lead Architect: Richard Baluku
 * Status: Production-Ready with Integrity Engine
 */
@Service
public class DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private LegalCaseRepository legalCaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * Process document upload with full chain of custody and SHA-256 Hashing.
     * FIXED: Now looks up user by username (since auth.getName() returns username)
     */
    @Transactional
    public Document processUpload(MultipartFile file, Long caseId, Authentication auth, String sourceOrigin) {

        // 1. PERSONA VERIFICATION - FIXED: Use findByUsername instead of findByEmail
        String authName = auth.getName();
        User uploader = userRepository.findByUsername(authName)
                .orElseThrow(() -> new RuntimeException("Security Breach: Authorized uploader not found for username: " + authName));

        // 2. CASE VERIFICATION
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Integrity Error: Targeted Case Vault does not exist."));

        // 3. ATOMIC STORAGE & HASHING (Integrity Engine Initiation)
        FileStorageService.FileUploadResult storageResult = fileStorageService.processAndSave(file);

        // 4. DUPLICATE HASH GUARD
        Optional<Object> existingDoc = documentRepository.findByFileHash(storageResult.fileHash());
        if (existingDoc.isPresent()) {
            throw new RuntimeException("Chain of Custody Error: This document fingerprint already exists in the system.");
        }

        // 5. METADATA PERSISTENCE
        Document doc = new Document();
        doc.setFileName(storageResult.fileName());
        doc.setFileType(storageResult.contentType());
        doc.setFilePath(storageResult.filePath());
        doc.setFileHash(storageResult.fileHash()); // THE DIGITAL STAMP

        // Safe handling of sourceOrigin
        doc.setSourceOrigin(sourceOrigin != null && !sourceOrigin.trim().isEmpty()
                ? sourceOrigin.trim()
                : "Registry Submission");

        doc.setUploadedBy(uploader.getUsername());
        doc.setLegalCase(legalCase);
        doc.setVersion(1);
        doc.setArchived(false);
        doc.setDocumentCategory("EVIDENCE"); // Explicitly set as evidence for clerk uploads

        Document savedDoc = documentRepository.save(doc);

        // 6. INTERNAL CASE LOGGING
        legalCase.addManualLog("📎 EVIDENCE UPLOADED: " + savedDoc.getFileName() + " by " + uploader.getUsername() + " (Source: " + sourceOrigin + ")");
        legalCaseRepository.save(legalCase);

        // 7. IMMUTABLE AUDIT LOGGING
        auditLogService.logAction(
                "EVIDENCE_UPLOADED",
                uploader.getUsername(),
                "Case: " + legalCase.getCaseNumber() +
                        " | File: " + savedDoc.getFileName() +
                        " | Fingerprint: " + storageResult.fileHash() +
                        " | Source: " + sourceOrigin
        );

        return savedDoc;
    }

    /**
     * THE INTEGRITY ENGINE: Verification Logic
     * Re-hashes the file on the server and compares it against the DB record.
     */
    public boolean verifyDocumentIntegrity(Long documentId) {
        try {
            Document doc = getDocumentById(documentId);
            if (doc == null) return false;

            Path filePath = Paths.get(doc.getFilePath());
            if (!Files.exists(filePath)) return false;

            // Generate fresh hash from the physical file
            String currentPhysicalHash = FileHasher.calculateHashFromPath(filePath);

            // Compare with the 'Digital Stamp' stored during upload
            return currentPhysicalHash.equals(doc.getFileHash());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Download a document with Automatic Integrity Shield.
     */
    public byte[] downloadDocument(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }

        // INTEGRITY SHIELD: Verify hash before returning bytes
        if (!verifyDocumentIntegrity(documentId)) {
            auditLogService.logAction("INTEGRITY_ALARM", "SYSTEM",
                    "TAMPER DETECTED: File ID " + documentId + " has been modified outside of Leviathan.");
            throw new RuntimeException("CRITICAL: Document integrity check failed. The file has been tampered with.");
        }

        // Original path-traversal logic maintained
        Path filePath = Paths.get(doc.getFilePath());
        if (!Files.exists(filePath)) {
            String altPath = "uploads/evidence/" + doc.getFileName();
            filePath = Paths.get(altPath);
            if (!Files.exists(filePath)) {
                throw new RuntimeException("File not found on server: " + doc.getFilePath());
            }
        }

        return Files.readAllBytes(filePath);
    }

    /**
     * Get document by file hash (for duplicate checking)
     */
    @SuppressWarnings("unchecked")
    public Document getDocumentByHash(String fileHash) {
        Optional<Object> result = documentRepository.findByFileHash(fileHash);
        return result.isPresent() ? (Document) result.get() : null;
    }

    /**
     * Archive a document (soft delete) - Chains to LegalCase Manual Log
     */
    @Transactional
    public Document archiveDocument(Long documentId) {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }
        doc.setArchived(true);

        LegalCase legalCase = doc.getLegalCase();
        if (legalCase != null) {
            legalCase.addManualLog("DOCUMENT ARCHIVED: " + doc.getFileName());
            legalCaseRepository.save(legalCase);
        }

        return documentRepository.save(doc);
    }

    /**
     * Retrieves all active (non-archived) evidence for a specific case.
     */
    public List<Document> getDocumentsByCaseId(Long caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("Case ID cannot be null");
        }
        return documentRepository.findByLegalCaseIdAndArchivedFalse(caseId);
    }

    /**
     * Get all documents for a case (including archived)
     */
    public List<Document> getAllDocumentsByCaseId(Long caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("Case ID cannot be null");
        }
        return documentRepository.findByLegalCaseId(caseId);
    }

    /**
     * Get a single document by its ID
     */
    public Document getDocumentById(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID cannot be null");
        }
        return documentRepository.findById(documentId).orElse(null);
    }
    /**
     * Get all documents (for clerk dashboard)
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }
    /**
     * Delete a document permanently (with physical file removal)
     */
    @Transactional
    public void deleteDocumentPermanently(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }

        Path filePath = Paths.get(doc.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        documentRepository.delete(doc);

        LegalCase legalCase = doc.getLegalCase();
        if (legalCase != null) {
            legalCase.addManualLog("DOCUMENT PERMANENTLY DELETED: " + doc.getFileName());
            legalCaseRepository.save(legalCase);
        }
    }
}