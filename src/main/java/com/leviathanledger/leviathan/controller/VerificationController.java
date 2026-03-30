package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public Verification Controller
 * Allows anyone (court clerks, process servers, opposing counsel) to verify
 * the authenticity of a legal document by scanning the QR code.
 */
@RestController
@RequestMapping("/api/verify")
@CrossOrigin(origins = "*") // Public endpoint - open to everyone
public class VerificationController {

    private static final Logger logger = LoggerFactory.getLogger(VerificationController.class);

    @Autowired
    private DocumentRepository documentRepository;

    /**
     * Verify a document by its file hash (SHA-256)
     * This is the QR code endpoint - when scanned, it opens this URL
     *
     * Example: https://leviathan.ug/verify/{fileHash}
     */
    @GetMapping("/{fileHash}")
    public ResponseEntity<?> verifyByHash(@PathVariable String fileHash) {
        logger.info("Public verification request for hash: {}", fileHash);

        Optional<Object> docOpt = documentRepository.findByFileHash(fileHash);

        if (docOpt.isEmpty()) {
            logger.warn("Verification failed: No document found with hash {}", fileHash);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "valid", false,
                            "message", "Document not found or has been revoked.",
                            "timestamp", java.time.LocalDateTime.now().toString()
                    ));
        }

        Document document = (Document) docOpt.get();

        // Build verification response
        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Document is authentic and verified by LexTracker Integrity Engine.");
        response.put("documentId", document.getId());
        response.put("documentType", document.getDisplayDocumentType());
        response.put("fileName", document.getFileName());
        response.put("fileSize", document.getFormattedFileSize());
        response.put("uploadedAt", document.getFormattedUploadedAt());
        response.put("uploadedBy", document.getUploadedBy());

        // Add certification details if document is certified
        if (document.isCertified()) {
            response.put("status", "CERTIFIED");
            response.put("certifiedAt", document.getFormattedCertifiedAt());
            response.put("certifiedBy", document.getCertifiedBy());
            response.put("verificationCode", document.getVerificationCode());
        } else if ("LEGAL_DOCUMENT".equals(document.getDocumentCategory())) {
            response.put("status", "DRAFT");
            response.put("message", "This document is a DRAFT and has not been certified for official use.");
        } else {
            response.put("status", "EVIDENCE");
            response.put("message", "This document is evidence uploaded to the case file.");
        }

        // Add case information
        LegalCase legalCase = document.getLegalCase();
        if (legalCase != null) {
            Map<String, Object> caseInfo = new HashMap<>();
            caseInfo.put("caseNumber", legalCase.getCaseNumber());
            caseInfo.put("caseTitle", legalCase.getTitle());
            caseInfo.put("clientName", legalCase.getClientName());
            caseInfo.put("status", legalCase.getStatus());
            response.put("case", caseInfo);
        }

        // Add audit trail summary
        if (document.isCertified()) {
            response.put("audit", Map.of(
                    "generated", document.getFormattedUploadedAt(),
                    "certified", document.getFormattedCertifiedAt(),
                    "integrityHash", document.getFileHash().substring(0, 16) + "..."
            ));
        } else {
            response.put("audit", Map.of(
                    "generated", document.getFormattedUploadedAt(),
                    "integrityHash", document.getFileHash().substring(0, 16) + "..."
            ));
        }

        logger.info("Verification successful for document: {} (ID: {})", document.getFileName(), document.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * Verify a document by verification code (shorter, user-friendly)
     * Example: https://leviathan.ug/verify/code/ABC12345
     */
    @GetMapping("/code/{verificationCode}")
    public ResponseEntity<?> verifyByCode(@PathVariable String verificationCode) {
        logger.info("Public verification request for code: {}", verificationCode);

        // Find document by verification code (only certified documents have codes)
        Optional<Document> docOpt = documentRepository.findAll().stream()
                .filter(doc -> verificationCode.equals(doc.getVerificationCode()))
                .findFirst();

        if (docOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "valid", false,
                            "message", "Invalid verification code.",
                            "timestamp", java.time.LocalDateTime.now().toString()
                    ));
        }

        Document document = docOpt.get();

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("message", "Document is authentic and verified.");
        response.put("documentType", document.getDisplayDocumentType());
        response.put("fileName", document.getFileName());
        response.put("certifiedAt", document.getFormattedCertifiedAt());
        response.put("certifiedBy", document.getCertifiedBy());

        LegalCase legalCase = document.getLegalCase();
        if (legalCase != null) {
            response.put("caseNumber", legalCase.getCaseNumber());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Simple health check for verification service
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "online",
                "service", "LexTracker Document Verification",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}