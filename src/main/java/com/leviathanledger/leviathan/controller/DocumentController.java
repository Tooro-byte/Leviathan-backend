package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    /**
     * Upload Endpoint: Receives physical file and metadata.
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
            return ResponseEntity.badRequest().body("Error: Cannot upload an empty case file.");
        }

        try {
            String personaName = (auth != null) ? auth.getName() : "Anonymous_Persona";
            logger.info("🛡️ B2 BOMBER: Persona {} uploading from [{}] for Case ID: {}",
                    personaName, sourceOrigin, caseId);

            Document doc = documentService.processUpload(file, caseId, auth, sourceOrigin);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            logger.error("🛡️ B2 BOMBER: Chain of Custody Breach: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Chain of Custody Error: " + e.getMessage());
        }
    }

    /**
     * Get Documents by Case - Fixed to prevent lazy loading / proxy serialization errors
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'JUDGE')")
    public ResponseEntity<?> getDocumentsByCase(@PathVariable Long caseId) {
        try {
            List<Document> documents = documentService.getDocumentsByCaseId(caseId);
            return ResponseEntity.ok(documents);
        } catch (Exception e) {
            logger.error("Vault Access Error for case {}: {}", caseId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Vault Access Error: " + e.getMessage());
        }
    }
}