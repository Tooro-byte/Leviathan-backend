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
     * Generate Notice of Intention to Sue
     */
    @PostMapping("/notice/{caseId}")
    public ResponseEntity<?> generateNotice(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "NOTICE_OF_INTENTION",
                pdfGeneratorService::generateNoticeOfIntention);
    }

    /**
     * Generate Summons to File Defence
     */
    @PostMapping("/summons/{caseId}")
    public ResponseEntity<?> generateSummons(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "SUMMONS_TO_FILE_DEFENCE",
                pdfGeneratorService::generateSummonsToFileDefence);
    }

    /**
     * Generate Originating Summons
     */
    @PostMapping("/originating/{caseId}")
    public ResponseEntity<?> generateOriginatingSummons(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "ORIGINATING_SUMMONS",
                pdfGeneratorService::generateOriginatingSummons);
    }

    /**
     * Generate Summons for Directions
     */
    @PostMapping("/directions/{caseId}")
    public ResponseEntity<?> generateSummonsForDirections(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "SUMMONS_FOR_DIRECTIONS",
                pdfGeneratorService::generateSummonsForDirections);
    }

    /**
     * Generate Application for Extension of Time
     */
    @PostMapping("/extension/{caseId}")
    public ResponseEntity<?> generateExtensionOfTime(@PathVariable Long caseId, Authentication auth) {
        return generateAndSaveDocument(caseId, auth, "EXTENSION_OF_TIME",
                pdfGeneratorService::generateExtensionOfTime);
    }

    /**
     * Core method to generate, save, and track documents
     */
    private ResponseEntity<?> generateAndSaveDocument(Long caseId, Authentication auth,
                                                      String documentType,
                                                      PdfGeneratorFunction generator) {
        // Validate caseId
        if (caseId == null) {
            logger.warn("Attempted to generate {} with null caseId", documentType);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Case ID is required"));
        }

        // Validate authentication
        if (auth == null || !auth.isAuthenticated()) {
            logger.warn("Unauthorized attempt to generate document");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String userName = auth.getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            // 1. Fetch case
            Optional<LegalCase> caseOptional = legalCaseService.getCaseById(caseId);
            if (caseOptional.isEmpty()) {
                logger.warn("Case not found with ID: {}", caseId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Case not found with ID: " + caseId));
            }

            LegalCase legalCase = caseOptional.get();

            // 2. Validate case has required fields
            if (legalCase.getCaseNumber() == null || legalCase.getCaseNumber().trim().isEmpty()) {
                logger.error("Case {} has no case number", caseId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Case has no case number"));
            }

            // 3. Generate PDF
            byte[] pdfBytes = generator.generate(legalCase);
            if (pdfBytes == null || pdfBytes.length == 0) {
                logger.error("Generated PDF is empty for case: {}", legalCase.getCaseNumber());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to generate PDF"));
            }

            // 4. Save PDF to file system
            FileStorageService.FileUploadResult storageResult = fileStorageService.saveGeneratedDocument(
                    pdfBytes, documentType, legalCase.getCaseNumber(), userName);

            // 5. Save document record to database using the updated Document entity
            Document document = new Document();
            document.setFileName(storageResult.fileName());
            document.setFileType("application/pdf");
            document.setFilePath(storageResult.filePath());
            document.setFileHash(storageResult.fileHash());
            document.setFileSize(storageResult.fileSize());
            document.setDocumentCategory("LEGAL_DOCUMENT");
            document.setDocumentType(documentType);
            document.setGenerationContext("PDF_GENERATOR");
            document.setSourceOrigin("Generated by LexTracker Automated Secretary");
            document.setUploadedBy(userName);
            document.setLegalCase(legalCase);
            document.setVersion(1);
            document.setArchived(false);

            Document savedDoc = documentRepository.save(document);

            // 6. ADD AUDIT LOG (Critical for Ugandan legal compliance)
            String auditMessage = String.format("📜 %s generated by %s at %s (Document ID: %d, Size: %d bytes)",
                    formatDocumentType(documentType), userName, timestamp, savedDoc.getId(), storageResult.fileSize());
            legalCase.addManualLog(auditMessage);
            legalCaseService.saveCase(legalCase);

            logger.info("Successfully generated and saved {} for case: {} (Document ID: {})",
                    documentType, legalCase.getCaseNumber(), savedDoc.getId());

            // 7. Return PDF for download
            String fileName = formatDocumentType(documentType).replace(" ", "_") + "_" + legalCase.getCaseNumber() + ".pdf";

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
     * Get all generated legal documents for a specific case
     */
    @GetMapping("/case/{caseId}")
    public ResponseEntity<?> getGeneratedDocuments(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentRepository.findByLegalCaseId(caseId);
            // Filter to only show generated legal documents (using the new documentCategory field)
            List<Document> generatedDocs = documents.stream()
                    .filter(doc -> doc.getDocumentCategory() != null &&
                            "LEGAL_DOCUMENT".equals(doc.getDocumentCategory()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(generatedDocs);
        } catch (Exception e) {
            logger.error("Error fetching documents for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch documents: " + e.getMessage()));
        }
    }

    /**
     * Get all evidence documents for a specific case
     */
    @GetMapping("/evidence/{caseId}")
    public ResponseEntity<?> getEvidenceDocuments(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentRepository.findByLegalCaseId(caseId);
            // Filter to only show evidence documents
            List<Document> evidenceDocs = documents.stream()
                    .filter(doc -> doc.getDocumentCategory() != null &&
                            "EVIDENCE".equals(doc.getDocumentCategory()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(evidenceDocs);
        } catch (Exception e) {
            logger.error("Error fetching evidence for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch evidence: " + e.getMessage()));
        }
    }

    /**
     * Get a specific document by ID
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<?> getDocument(@PathVariable Long documentId) {
        try {
            Optional<Document> documentOpt = documentRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }
            return ResponseEntity.ok(documentOpt.get());
        } catch (Exception e) {
            logger.error("Error fetching document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch document: " + e.getMessage()));
        }
    }

    /**
     * Download a document by ID
     */
    @GetMapping("/download/{documentId}")
    public ResponseEntity<?> downloadDocument(@PathVariable Long documentId) {
        try {
            Optional<Document> documentOpt = documentRepository.findById(documentId);
            if (documentOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Document not found"));
            }

            Document document = documentOpt.get();
            byte[] fileData = fileStorageService.readFile(document.getFilePath());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(fileData);

        } catch (Exception e) {
            logger.error("Error downloading document {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to download document: " + e.getMessage()));
        }
    }

    /**
     * Format document type for display
     */
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

    /**
     * Functional interface for PDF generation methods
     */
    @FunctionalInterface
    private interface PdfGeneratorFunction {
        byte[] generate(LegalCase legalCase);
    }
}