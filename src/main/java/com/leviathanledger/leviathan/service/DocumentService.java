package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
import com.leviathanledger.leviathan.util.FileHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LEVIATHAN LEDGER V2.0 - CORE DOCUMENT SERVICE
 * Lead Architect: Richard Baluku
 * Status: Production-Ready | LazyInitializationException FIXED with embedded EvidenceRecord
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

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
     * Embedded clean record (no separate DTO file) - Type-safe & Clean
     */
    public record EvidenceRecord(
            Long id,
            String fileName,
            String fileType,
            Long fileSize,
            LocalDateTime uploadedAt,
            String sourceOrigin,
            String uploadedBy,
            String fileHash,
            Long caseId,
            String caseNumber,
            String caseTitle
    ) {}

    /**
     * Process document upload with full chain of custody and SHA-256 Hashing.
     */
    @Transactional
    public Document processUpload(MultipartFile file, Long caseId, Authentication auth, String sourceOrigin) {
        String authName = auth.getName();
        User uploader = userRepository.findByUsername(authName)
                .orElseThrow(() -> new RuntimeException("Security Breach: Authorized uploader not found: " + authName));

        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Integrity Error: Targeted Case Vault does not exist."));

        FileStorageService.FileUploadResult storageResult = fileStorageService.processAndSave(file);

        Optional<Document> existingDoc = documentRepository.findByFileHash(storageResult.fileHash());
        if (existingDoc.isPresent()) {
            throw new RuntimeException("Chain of Custody Error: This document fingerprint already exists.");
        }

        Document doc = new Document();
        doc.setFileName(storageResult.fileName());
        doc.setFileType(storageResult.contentType());
        doc.setFilePath(storageResult.filePath());
        doc.setFileHash(storageResult.fileHash());
        doc.setFileSize(storageResult.fileSize());
        doc.setUploadedBy(uploader.getUsername());
        doc.setLegalCase(legalCase);
        doc.setVersion(1);
        doc.setArchived(false);
        doc.setDocumentCategory("EVIDENCE");
        doc.setSourceOrigin(sourceOrigin != null ? sourceOrigin.trim() : "Registry Submission");

        Document savedDoc = documentRepository.save(doc);

        legalCase.addManualLog("📎 EVIDENCE UPLOADED: " + savedDoc.getFileName() + " by " + uploader.getUsername());
        legalCaseRepository.save(legalCase);

        auditLogService.logAction("EVIDENCE_UPLOADED", uploader.getUsername(),
                "Case: " + legalCase.getCaseNumber() + " | Hash: " + storageResult.fileHash());

        return savedDoc;
    }

    /**
     * FIXED: Returns clean EvidenceRecord list - All lazy loading happens safely here
     */
    @Transactional(readOnly = true)
    public List<EvidenceRecord> getAllEvidenceForClerk() {
        logger.info("Clerk Dashboard: Fetching all evidence documents using embedded EvidenceRecord");

        List<Document> allDocuments = documentRepository.findAll();
        List<EvidenceRecord> evidenceList = new ArrayList<>();

        for (Document doc : allDocuments) {
            if (!"EVIDENCE".equalsIgnoreCase(doc.getDocumentCategory())) {
                continue;
            }

            LegalCase legalCase = doc.getLegalCase();

            EvidenceRecord record = new EvidenceRecord(
                    doc.getId(),
                    doc.getFileName(),
                    doc.getFileType(),
                    doc.getFileSize(),
                    doc.getUploadedAt(),
                    doc.getSourceOrigin(),
                    doc.getUploadedBy(),
                    doc.getFileHash(),
                    legalCase != null ? legalCase.getId() : null,
                    legalCase != null ? legalCase.getCaseNumber() : "Unknown Case",
                    legalCase != null ? legalCase.getTitle() : "No Case Linked"
            );

            evidenceList.add(record);
        }

        // Sort by uploadedAt descending
        evidenceList.sort((a, b) -> {
            if (a.uploadedAt() == null || b.uploadedAt() == null) return 0;
            return b.uploadedAt().compareTo(a.uploadedAt());
        });

        logger.info("Clerk Dashboard: Returning {} evidence records", evidenceList.size());
        return evidenceList;
    }

    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByCaseId(Long caseId) {
        return documentRepository.findByLegalCaseIdAndArchivedFalse(caseId);
    }

    @Transactional(readOnly = true)
    public Document getDocumentByHash(String fileHash) {
        return documentRepository.findByFileHash(fileHash).orElse(null);
    }

    @Transactional
    public Document archiveDocument(Long documentId) {
        Document doc = getDocumentById(documentId);
        if (doc == null) throw new RuntimeException("Document not found.");

        doc.setArchived(true);
        if (doc.getLegalCase() != null) {
            doc.getLegalCase().addManualLog("DOCUMENT ARCHIVED: " + doc.getFileName());
        }
        return documentRepository.save(doc);
    }

    @Transactional
    public void deleteDocumentPermanently(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) return;

        Path path = Paths.get(doc.getFilePath());
        if (Files.exists(path)) Files.delete(path);

        if (doc.getLegalCase() != null) {
            doc.getLegalCase().addManualLog("DOCUMENT DELETED: " + doc.getFileName());
        }
        documentRepository.delete(doc);
    }

    @Transactional(readOnly = true)
    public boolean verifyDocumentIntegrity(Long documentId) {
        try {
            Document doc = getDocumentById(documentId);
            if (doc == null) return false;

            Path path = Paths.get(doc.getFilePath());
            if (!Files.exists(path)) return false;

            String currentHash = FileHasher.calculateHashFromPath(path);
            return currentHash.equals(doc.getFileHash());
        } catch (Exception e) {
            logger.error("Integrity verification failed for doc ID {}", documentId, e);
            return false;
        }
    }

    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long documentId) throws IOException {
        Document doc = getDocumentById(documentId);
        if (doc == null) throw new RuntimeException("Document not found.");

        if (!verifyDocumentIntegrity(documentId)) {
            auditLogService.logAction("INTEGRITY_ALARM", "SYSTEM", "TAMPER DETECTED: ID " + documentId);
            throw new RuntimeException("CRITICAL: Document integrity check failed. Possible tampering.");
        }

        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) throw new RuntimeException("File missing on server.");

        return Files.readAllBytes(path);
    }
}