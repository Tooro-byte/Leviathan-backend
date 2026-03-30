package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.service.DocumentService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    /**
     * Upload Endpoint: Receives physical file and metadata.
     * Only LAWYER and CLERK can upload documents.
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
            return ResponseEntity.internalServerError().body(Map.of("error", "Chain of Custody Error: " + e.getMessage()));
        }
    }

    /**
     * Get Documents by Case - Safe serialization with null handling
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<?> getDocumentsByCase(@PathVariable Long caseId) {
        try {
            logger.info("Fetching documents for case ID: {}", caseId);
            List<Document> documents = documentService.getDocumentsByCaseId(caseId);

            // Convert to safe DTO to prevent null serialization issues
            List<Map<String, Object>> safeDocuments = documents.stream().map(doc -> {
                Map<String, Object> map = new HashMap<>();
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
                return map;
            }).collect(Collectors.toList());

            logger.info("Returning {} documents for case {}", safeDocuments.size(), caseId);
            return ResponseEntity.ok(safeDocuments);
        } catch (Exception e) {
            logger.error("Vault Access Error for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Vault Access Error: " + e.getMessage()));
        }
    }

    /**
     * Download a specific document
     */
    @GetMapping("/download/{documentId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<?> downloadDocument(@PathVariable Long documentId) {
        try {
            Document doc = documentService.getDocumentById(documentId);
            if (doc == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            byte[] fileData = documentService.downloadDocument(documentId);

            String contentType = doc.getFileType() != null ? doc.getFileType() : "application/octet-stream";
            String filename = doc.getFileName() != null ? doc.getFileName() : "document";

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", contentType)
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Document download error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Download failed: " + e.getMessage()));
        }
    }

    /**
     * View/Preview a document (opens in browser)
     */
    @GetMapping("/view/{documentId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'CLIENT')")
    public ResponseEntity<?> viewDocument(@PathVariable Long documentId) {
        try {
            Document doc = documentService.getDocumentById(documentId);
            if (doc == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            byte[] fileData = documentService.downloadDocument(documentId);
            String contentType = doc.getFileType() != null ? doc.getFileType() : "application/pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + doc.getFileName() + "\"")
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Document view error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "View failed: " + e.getMessage()));
        }
    }
}