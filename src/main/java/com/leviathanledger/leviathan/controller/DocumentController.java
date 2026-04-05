package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.service.DocumentService;
import com.leviathanledger.leviathan.service.DocumentService.EvidenceRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    /**
     * Upload Endpoint
     */
    @PostMapping(value = "/upload/{caseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('LAWYER') or hasRole('CLERK')")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long caseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceOrigin") String sourceOrigin,
            Authentication auth,
            HttpServletRequest request) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot upload an empty case file."));
        }

        try {
            String personaName = (auth != null) ? auth.getName() : "Anonymous_Persona";
            logger.info("🛡️ B2 BOMBER: Persona {} uploading from [{}] for Case ID: {}",
                    personaName, sourceOrigin, caseId);

            Document doc = documentService.processUpload(file, caseId, auth, sourceOrigin);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            logger.error("🛡️ B2 BOMBER: Chain of Custody Breach: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Chain of Custody Error: " + e.getMessage()));
        }
    }

    /**
     * Get Documents by Case
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<?> getDocumentsByCase(@PathVariable Long caseId) {
        try {
            logger.info("Fetching documents for case ID: {}", caseId);
            List<Document> documents = documentService.getDocumentsByCaseId(caseId);

            List<Map<String, Object>> safeDocuments = documents.stream().map(doc -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", doc.getId());
                map.put("fileName", doc.getFileName());
                map.put("fileType", doc.getFileType());
                map.put("fileSize", doc.getFileSize());
                map.put("uploadedAt", doc.getUploadedAt());
                map.put("documentCategory", doc.getDocumentCategory());
                map.put("documentType", doc.getDocumentType());
                map.put("certified", doc.isCertified());
                map.put("certifiedBy", doc.getCertifiedBy());
                map.put("certifiedAt", doc.getCertifiedAt());
                map.put("verificationCode", doc.getVerificationCode());
                map.put("verificationUrl", doc.getVerificationUrl());
                map.put("uploadedBy", doc.getUploadedBy());
                map.put("displayDocumentType", doc.getDisplayDocumentType());
                map.put("fileHash", doc.getFileHash());  // ✅ FIXED: Added fileHash to response
                return map;
            }).collect(java.util.stream.Collectors.toList());

            logger.info("Returning {} documents for case {}", safeDocuments.size(), caseId);
            return ResponseEntity.ok(safeDocuments);
        } catch (Exception e) {
            logger.error("Vault Access Error for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Vault Access Error: " + e.getMessage()));
        }
    }

    /**
     * Get all evidence documents for Clerk Dashboard
     */
    @GetMapping("/evidence")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK')")
    public ResponseEntity<List<EvidenceRecord>> getAllEvidence() {
        try {
            logger.info("Clerk Dashboard: Fetching all evidence documents using EvidenceRecord");
            List<EvidenceRecord> evidenceDocs = documentService.getAllEvidenceForClerk();
            logger.info("Clerk Dashboard: Returning {} evidence records", evidenceDocs.size());
            return ResponseEntity.ok(evidenceDocs);
        } catch (Exception e) {
            logger.error("Error fetching evidence documents for Clerk: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Assigned Tasks Endpoint
     */
    @GetMapping("/tasks/assigned")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK')")
    public ResponseEntity<List<Map<String, Object>>> getAssignedTasks(Authentication auth) {
        String username = auth != null ? auth.getName() : "unknown";
        logger.info("Fetching assigned tasks for user: {}", username);

        List<Map<String, Object>> tasks = List.of(
                Map.of("id", 101, "title", "Review uploaded evidence for Case LEX-UG-2026-1424-KLA",
                        "status", "PENDING", "priority", "HIGH", "dueDate", "2026-04-10"),
                Map.of("id", 102, "title", "Prepare Summons to File Defence for Case LEX-UG-2026-5537-KLA",
                        "status", "IN_PROGRESS", "priority", "MEDIUM", "dueDate", "2026-04-05")
        );

        return ResponseEntity.ok(tasks);
    }

    /**
     * DOWNLOAD DOCUMENT - Fully Fixed
     */
    @GetMapping("/download/{documentId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentId) {
        try {
            logger.info("Download request received for document ID: {}", documentId);

            Document doc = documentService.getDocumentById(documentId);
            if (doc == null) {
                logger.warn("Document not found: ID {}", documentId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            byte[] fileData = documentService.downloadDocument(documentId);

            String contentType = doc.getFileType() != null ? doc.getFileType() : "application/octet-stream";
            String filename = doc.getFileName() != null ? doc.getFileName() : "document.pdf";

            logger.info("Serving download: {} ({} bytes, type: {})", filename, fileData.length, contentType);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", contentType)
                    .header("Content-Length", String.valueOf(fileData.length))
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Document download error for ID {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * VIEW DOCUMENT (opens in new tab/browser)
     */
    @GetMapping("/view/{documentId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<byte[]> viewDocument(@PathVariable Long documentId) {
        try {
            logger.info("View request received for document ID: {}", documentId);

            Document doc = documentService.getDocumentById(documentId);
            if (doc == null) {
                logger.warn("Document not found for view: ID {}", documentId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            byte[] fileData = documentService.downloadDocument(documentId);
            String contentType = doc.getFileType() != null ? doc.getFileType() : "application/pdf";

            logger.info("Serving view for: {} (type: {})", doc.getFileName(), contentType);

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + doc.getFileName() + "\"")
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Document view error for ID {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}