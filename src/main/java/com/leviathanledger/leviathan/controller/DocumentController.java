package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * LexTracker Integrity Engine: Document Controller
 * Purpose: Entry point for all legal evidence. Enforces Persona-based
 * upload permissions and initiates the SHA-256 Chain of Custody.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    @Autowired
    private DocumentService documentService;

    /**
     * Uploads a document to a specific Case Vault.
     * Restricted to LAWYER and CLERK roles.
     */
    @PostMapping("/upload/{caseId}")
    @PreAuthorize("hasRole('LAWYER') or hasRole('CLERK')")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long caseId,
            @RequestParam("file") MultipartFile file,
            Authentication auth,
            HttpServletRequest request) { // Explicitly injected to prevent 'request is null' error

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Cannot upload an empty case file.");
        }

        try {
            String personaName = (auth != null) ? auth.getName() : "Anonymous_Persona";
            logger.info("Persona {} initiating upload for Case ID: {} from IP: {}",
                    personaName, caseId, request.getRemoteAddr());

            // We pass the file, ID, and auth to the service
            Document doc = documentService.processUpload(file, caseId, auth);

            return ResponseEntity.ok(doc);

        } catch (Exception e) {
            // Log the full stack trace to catch exactly where the NullPointer happens
            logger.error("Chain of Custody Breach: Upload failed for file {} - {}",
                    file.getOriginalFilename(), e.getMessage(), e);

            return ResponseEntity.internalServerError()
                    .body("Chain of Custody Error: " + e.getMessage());
        }
    }

    /**
     * Retrieves all documents associated with a case.
     */
    @GetMapping("/case/{caseId}")
    @PreAuthorize("hasAnyRole('LAWYER', 'CLERK', 'JUDGE')")
    public ResponseEntity<?> getDocumentsByCase(@PathVariable Long caseId) {
        try {
            return ResponseEntity.ok(documentService.getDocumentsByCaseId(caseId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Vault Access Error: " + e.getMessage());
        }
    }
}