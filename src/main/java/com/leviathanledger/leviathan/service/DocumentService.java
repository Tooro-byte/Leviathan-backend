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

import java.util.List;

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
        User uploader = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Security Breach: Authorized uploader not found."));

        // 2. CASE VERIFICATION
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Integrity Error: Targeted Case Vault does not exist."));

        // 3. ATOMIC STORAGE & HASHING
        FileStorageService.FileUploadResult storageResult = fileStorageService.processAndSave(file);

        // 4. DUPLICATE HASH GUARD
        documentRepository.findByFileHash(storageResult.fileHash()).ifPresent(existing -> {
            throw new RuntimeException("Chain of Custody Error: This document fingerprint already exists in the system.");
        });

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

        // 6. IMMUTABLE AUDIT LOGGING
        auditLogService.logAction(
                "DOCUMENT_CERTIFIED",
                uploader.getUsername(),
                "Case: " + legalCase.getCaseNumber() +
                        " | Origin: " + (sourceOrigin != null ? sourceOrigin : "Unknown") +
                        " | Fingerprint: " + storageResult.fileHash()
        );

        return savedDoc;
    }

    /**
     * Retrieves all active (non-archived) evidence for a specific case.
     * This method is called by DocumentController.getDocumentsByCase()
     */
    public List<Document> getDocumentsByCaseId(Long caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("Case ID cannot be null");
        }
        return documentRepository.findByLegalCaseIdAndArchivedFalse(caseId);
    }
}