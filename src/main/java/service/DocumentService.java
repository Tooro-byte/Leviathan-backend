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

    @Autowired private DocumentRepository documentRepository;
    @Autowired private LegalCaseRepository legalCaseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.leviathanledger.leviathan.service.FileStorageService fileStorageService;
    @Autowired private com.leviathanledger.leviathan.service.AuditLogService auditLogService;

    @Transactional
    public Document processUpload(MultipartFile file, Long caseId, Authentication auth) {

        // 1. PERSONA VERIFICATION
        User uploader = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Security Breach: Authorized uploader not found."));

        // 2. CASE VERIFICATION
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Integrity Error: Targeted Case Vault does not exist."));

        // 3. ATOMIC STORAGE & HASHING
        com.leviathanledger.leviathan.service.FileStorageService.FileUploadResult storageResult = fileStorageService.processAndSave(file);

        // 4. DUPLICATE HASH GUARD
        // This prevents the "Duplicate Entry" SQL error by checking the fingerprint first.
        documentRepository.findByFileHash(storageResult.fileHash()).ifPresent(existing -> {
            throw new RuntimeException("Chain of Custody Error: This document fingerprint already exists in the system.");
        });

        // 5. METADATA PERSISTENCE
        Document doc = new Document();
        doc.setFileName(storageResult.fileName());
        doc.setFileType(storageResult.contentType());
        doc.setFilePath(storageResult.filePath());
        doc.setFileHash(storageResult.fileHash());
        String sourceOrigin = "";
        doc.setSourceOrigin(sourceOrigin);
        doc.setUploadedBy(uploader.getUsername());
        doc.setLegalCase(legalCase);
        doc.setVersion(1);
        doc.setArchived(false);

        Document savedDoc = documentRepository.save(doc);

        // 6. IMMUTABLE AUDIT LOGGING
        auditLogService.logAction(
                "DOCUMENT_CERTIFIED",
                uploader.getUsername(),
                "Case: " + legalCase.getCaseNumber() +
                        " | Origin: " + sourceOrigin +
                        " | Fingerprint: " + storageResult.fileHash()
        );

        return savedDoc;
    }

    /**
     * Retrieves all non-archived documents for a specific case.
     */
    public List<Document> getDocumentsByCaseId(Long caseId) {
        return documentRepository.findByLegalCaseIdAndArchivedFalse(caseId);
    }
}