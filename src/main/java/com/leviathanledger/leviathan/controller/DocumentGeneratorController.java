package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import com.leviathanledger.leviathan.service.FileStorageService;
import com.leviathanledger.leviathan.service.LegalCaseService;
import com.leviathanledger.leviathan.service.PdfGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/generate")
public class DocumentGeneratorController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentGeneratorController.class);

    private final PdfGeneratorService pdfGeneratorService;
    private final LegalCaseService legalCaseService;
    private final FileStorageService fileStorageService;
    private final DocumentRepository documentRepository;

    @Autowired
    public DocumentGeneratorController(PdfGeneratorService pdfGeneratorService,
                                       LegalCaseService legalCaseService,
                                       FileStorageService fileStorageService,
                                       DocumentRepository documentRepository) {
        this.pdfGeneratorService = pdfGeneratorService;
        this.legalCaseService = legalCaseService;
        this.fileStorageService = fileStorageService;
        this.documentRepository = documentRepository;
    }

    /**
     * Generate Notice of Intention to Sue (DRAFT for Clerk)
     */
    @PostMapping("/notice/{caseId}")
    public ResponseEntity<?> generateNotice(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "NOTICE_OF_INTENTION",
                (legalCase) -> pdfGeneratorService.generateNoticeOfIntention(legalCase, true));
    }

    /**
     * Generate Summons to File Defence (DRAFT for Clerk)
     */
    @PostMapping("/summons/{caseId}")
    public ResponseEntity<?> generateSummons(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "SUMMONS_TO_FILE_DEFENCE",
                (legalCase) -> pdfGeneratorService.generateSummonsToFileDefence(legalCase, true));
    }

    /**
     * Generate Originating Summons (DRAFT for Clerk)
     */
    @PostMapping("/originating/{caseId}")
    public ResponseEntity<?> generateOriginatingSummons(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "ORIGINATING_SUMMONS",
                (legalCase) -> pdfGeneratorService.generateOriginatingSummons(legalCase, true));
    }

    /**
     * Generate Summons for Directions (DRAFT for Clerk)
     */
    @PostMapping("/directions/{caseId}")
    public ResponseEntity<?> generateSummonsForDirections(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "SUMMONS_FOR_DIRECTIONS",
                (legalCase) -> pdfGeneratorService.generateSummonsForDirections(legalCase, true));
    }

    /**
     * Generate Extension of Time (DRAFT for Clerk)
     */
    @PostMapping("/extension/{caseId}")
    public ResponseEntity<?> generateExtensionOfTime(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "EXTENSION_OF_TIME",
                (legalCase) -> pdfGeneratorService.generateExtensionOfTime(legalCase, true));
    }

    /**
     * CERTIFY a document - Lawyer approves and seals the document
     */
    @PostMapping("/certify/{documentId}")
    public ResponseEntity<?> certifyDocument(@PathVariable Long documentId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        try {
            Optional<Document> docOpt = documentRepository.findById(documentId);
            if (docOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            Document document = docOpt.get();

            // Debug logging
            logger.info("Attempting to certify document ID: {}, Category: {}, Certified: {}",
                    documentId, document.getDocumentCategory(), document.isCertified());

            // Can only certify legal documents that are not already certified
            if (!"LEGAL_DOCUMENT".equals(document.getDocumentCategory())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only legal documents can be certified. Current category: " + document.getDocumentCategory()));
            }

            if (document.isCertified()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Document is already certified"));
            }

            // Fetch the associated case
            Optional<LegalCase> caseOpt = legalCaseService.getCaseById(document.getLegalCase().getId());
            if (caseOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Associated case not found"));
            }

            LegalCase legalCase = caseOpt.get();
            String certifiedBy = auth.getName();
            String verificationCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String verificationUrl = "https://leviathan.ug/verify/" + document.getFileHash();

            // Read the draft PDF from storage
            byte[] draftPdf = fileStorageService.readFile(document.getFilePath());

            // Generate certified PDF (remove watermark, add Lex Stamp and QR Code)
            byte[] certifiedPdf = pdfGeneratorService.certifyDocument(
                    draftPdf, legalCase, certifiedBy, verificationUrl, verificationCode
            );

            // Save the certified PDF
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String certifiedFileName = document.getFileName().replace(".pdf", "_CERTIFIED_" + timestamp + ".pdf");

            FileStorageService.FileUploadResult storageResult = fileStorageService.saveGeneratedDocument(
                    certifiedPdf, document.getDocumentType(), legalCase.getCaseNumber(), certifiedBy
            );

            // Update document record
            document.setCertified(true);
            document.setCertifiedAt(LocalDateTime.now());
            document.setCertifiedBy(certifiedBy);
            document.setVerificationUrl(verificationUrl);
            document.setVerificationCode(verificationCode);
            document.setFilePath(storageResult.filePath());
            document.setFileName(certifiedFileName);
            document.setFileHash(storageResult.fileHash());
            document.setFileSize(storageResult.fileSize());

            documentRepository.save(document);

            // Add audit log
            String auditMessage = String.format("✓ CERTIFIED: %s certified by %s (Verification Code: %s)",
                    document.getDisplayDocumentTypeName(), certifiedBy, verificationCode);
            legalCase.addManualLog(auditMessage);
            legalCaseService.saveCase(legalCase);

            logger.info("Document {} certified by {}", documentId, certifiedBy);

            return ResponseEntity.ok(Map.of(
                    "message", "Document certified successfully",
                    "documentId", document.getId(),
                    "verificationCode", verificationCode,
                    "verificationUrl", verificationUrl
            ));

        } catch (Exception e) {
            logger.error("Certification failed for document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Certification failed: " + e.getMessage()));
        }
    }

    /**
     * DOWNLOAD a document by ID - FIXED ENDPOINT
     */
    @GetMapping("/download/{documentId}")
    public ResponseEntity<?> downloadDocument(@PathVariable Long documentId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        try {
            Optional<Document> documentOpt = documentRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            Document document = documentOpt.get();

            // Log the download attempt
            logger.info("Downloading document ID: {}, Category: {}, File: {}",
                    documentId, document.getDocumentCategory(), document.getFileName());

            // Read file from storage
            byte[] fileData = fileStorageService.readFile(document.getFilePath());

            String contentType = document.getFileType() != null ? document.getFileType() : "application/pdf";
            String filename = document.getFileName() != null ? document.getFileName() : "document.pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Error downloading document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Download failed: " + e.getMessage()));
        }
    }

    /**
     * VIEW/Preview a document in browser
     */
    @GetMapping("/view/{documentId}")
    public ResponseEntity<?> viewDocument(@PathVariable Long documentId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        try {
            Optional<Document> documentOpt = documentRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            Document document = documentOpt.get();
            byte[] fileData = fileStorageService.readFile(document.getFilePath());
            String contentType = document.getFileType() != null ? document.getFileType() : "application/pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName() + "\"")
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Error viewing document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "View failed: " + e.getMessage()));
        }
    }

    /**
     * Core method to generate, save, and track documents (DRAFT version)
     */
    private ResponseEntity<?> generateAndSaveDocument(Long caseId, Authentication auth,
                                                      String documentType,
                                                      PdfGeneratorFunction generator) {
        if (caseId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Case ID is required"));
        }

        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String userName = auth.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            Optional<LegalCase> caseOptional = legalCaseService.getCaseById(caseId);
            if (caseOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Case not found with ID: " + caseId));
            }

            LegalCase legalCase = caseOptional.get();

            if (legalCase.getCaseNumber() == null || legalCase.getCaseNumber().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Case has no case number"));
            }

            // Generate DRAFT PDF (with watermark)
            byte[] pdfBytes = generator.generate(legalCase);
            if (pdfBytes == null || pdfBytes.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to generate PDF"));
            }

            // Save to file system
            FileStorageService.FileUploadResult storageResult = fileStorageService.saveGeneratedDocument(
                    pdfBytes, documentType, legalCase.getCaseNumber(), userName);

            // Save document record
            Document document = new Document();
            document.setFileName(storageResult.fileName());
            document.setFileType("application/pdf");
            document.setFilePath(storageResult.filePath());
            document.setFileHash(storageResult.fileHash());
            document.setFileSize(storageResult.fileSize());
            document.setDocumentCategory("LEGAL_DOCUMENT");
            document.setDocumentType(documentType);
            document.setGenerationContext("PDF_GENERATOR");
            document.setSourceOrigin("Generated by LexTracker Automated Secretary (DRAFT)");
            document.setUploadedBy(userName);
            document.setLegalCase(legalCase);
            document.setVersion(1);
            document.setArchived(false);
            document.setCertified(false); // DRAFT

            Document savedDoc = documentRepository.save(document);

            // Add audit log
            String auditMessage = String.format("📄 %s (DRAFT) generated by %s (Document ID: %d)",
                    formatDocumentType(documentType), userName, savedDoc.getId());
            legalCase.addManualLog(auditMessage);
            legalCaseService.saveCase(legalCase);

            logger.info("Generated DRAFT {} for case: {} (Document ID: {})",
                    documentType, legalCase.getCaseNumber(), savedDoc.getId());

            String fileName = formatDocumentType(documentType).replace(" ", "_") + "_DRAFT_" + legalCase.getCaseNumber() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);

        } catch (Exception e) {
            logger.error("Error generating {} for case ID {}: {}", documentType, caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate document: " + e.getMessage()));
        }
    }

    /**
     * Get all documents for a case (with certification status)
     */
    @GetMapping("/case/{caseId}")
    public ResponseEntity<?> getDocuments(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentRepository.findByLegalCaseId(caseId);

            // Convert to safe DTO to prevent null serialization issues
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
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(safeDocuments);
        } catch (Exception e) {
            logger.error("Error fetching documents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }

    /**
     * Get only certified documents for a case
     */
    @GetMapping("/case/{caseId}/certified")
    public ResponseEntity<?> getCertifiedDocuments(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentRepository.findByLegalCaseId(caseId);
            List<Map<String, Object>> certifiedDocs = documents.stream()
                    .filter(Document::isCertified)
                    .map(doc -> {
                        Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", doc.getId());
                        map.put("fileName", doc.getFileName());
                        map.put("documentType", doc.getDocumentType());
                        map.put("certifiedBy", doc.getCertifiedBy());
                        map.put("certifiedAt", doc.getCertifiedAt());
                        map.put("verificationCode", doc.getVerificationCode());
                        map.put("displayDocumentType", doc.getDisplayDocumentType());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(certifiedDocs);
        } catch (Exception e) {
            logger.error("Error fetching certified documents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }

    /**
     * Get only draft documents for a case
     */
    @GetMapping("/case/{caseId}/drafts")
    public ResponseEntity<?> getDraftDocuments(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentRepository.findByLegalCaseId(caseId);
            List<Map<String, Object>> draftDocs = documents.stream()
                    .filter(doc -> !doc.isCertified() && "LEGAL_DOCUMENT".equals(doc.getDocumentCategory()))
                    .map(doc -> {
                        Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", doc.getId());
                        map.put("fileName", doc.getFileName());
                        map.put("documentType", doc.getDocumentType());
                        map.put("uploadedAt", doc.getUploadedAt());
                        map.put("uploadedBy", doc.getUploadedBy());
                        map.put("displayDocumentType", doc.getDisplayDocumentType());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(draftDocs);
        } catch (Exception e) {
            logger.error("Error fetching draft documents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }

    private String formatDocumentType(String documentType) {
        return switch (documentType) {
            case "NOTICE_OF_INTENTION" -> "Notice of Intention to Sue";
            case "SUMMONS_TO_FILE_DEFENCE" -> "Summons to File Defence";
            case "ORIGINATING_SUMMONS" -> "Originating Summons";
            case "SUMMONS_FOR_DIRECTIONS" -> "Summons for Directions";
            case "EXTENSION_OF_TIME" -> "Application for Extension of Time";
            default -> documentType.replace("_", " ");
        };
    }

    @FunctionalInterface
    private interface PdfGeneratorFunction {
        byte[] generate(LegalCase legalCase);
    }
}