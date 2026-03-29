package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
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
     * Process document upload with full chain of custody
     */
    @Transactional
    public Document processUpload(MultipartFile file, Long caseId, Authentication auth, String sourceOrigin) {

        // 1. PERSONA VERIFICATION
        User uploader = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Security Breach: Authorized uploader not found."));

        // 2. CASE VERIFICATION
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Integrity Error: Targeted Case Vault does not exist."));

        // 3. ATOMIC STORAGE & HASHING
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
        doc.setFileHash(storageResult.fileHash());

        // Safe handling of sourceOrigin (prevents null issues)
        doc.setSourceOrigin(sourceOrigin != null && !sourceOrigin.trim().isEmpty()
                ? sourceOrigin.trim()
                : "Unknown Origin");

        doc.setUploadedBy(uploader.getUsername());
        doc.setLegalCase(legalCase);
        doc.setVersion(1);
        doc.setArchived(false);

        // Save document
        Document savedDoc = documentRepository.save(doc);

        // Add audit log to the case
        legalCase.addManualLog("DOCUMENT UPLOADED: " + savedDoc.getFileName() + " by " + uploader.getUsername());
        legalCaseRepository.save(legalCase);

        // 6. IMMUTABLE AUDIT LOGGING
        auditLogService.logAction(
                "DOCUMENT_CERTIFIED",
                uploader.getUsername(),
                "Case: " + legalCase.getCaseNumber() +
                        " | File: " + savedDoc.getFileName() +
                        " | Origin: " + (sourceOrigin != null ? sourceOrigin : "Unknown") +
                        " | Fingerprint: " + storageResult.fileHash()
        );

        return savedDoc;
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
     * Get a single document by its ID
     */
    public Document getDocumentById(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID cannot be null");
        }
        Optional<Document> doc = documentRepository.findById(documentId);
        return doc.orElse(null);
    }

    /**
     * Download a document and return its byte array
     */
    public byte[] downloadDocument(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }

        // Try multiple possible paths
        Path filePath = Paths.get(doc.getFilePath());
        if (!Files.exists(filePath)) {
            // Try alternative path - just filename in uploads/evidence
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
     * Archive a document (soft delete)
     */
    @Transactional
    public Document archiveDocument(Long documentId) {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }
        doc.setArchived(true);

        // Add audit log
        LegalCase legalCase = doc.getLegalCase();
        if (legalCase != null) {
            legalCase.addManualLog("DOCUMENT ARCHIVED: " + doc.getFileName());
            legalCaseRepository.save(legalCase);
        }

        return documentRepository.save(doc);
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
     * Delete a document permanently (use with caution)
     */
    @Transactional
    public void deleteDocumentPermanently(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) {
            throw new RuntimeException("Document not found with ID: " + documentId);
        }

        // Delete physical file
        Path filePath = Paths.get(doc.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // Delete database record
        documentRepository.delete(doc);

        // Add audit log
        LegalCase legalCase = doc.getLegalCase();
        if (legalCase != null) {
            legalCase.addManualLog("DOCUMENT PERMANENTLY DELETED: " + doc.getFileName());
            legalCaseRepository.save(legalCase);
        }
    }
}