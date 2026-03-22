package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.service.LegalCaseService;
import com.leviathanledger.leviathan.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@CrossOrigin(origins = "*") // In production, replace * with your frontend URL
public class LegalCaseController {

    @Autowired private LegalCaseService legalCaseService;
    @Autowired private AuditLogService auditLogService;

    // Define a root location for uploads
    private final Path root = Paths.get("uploads");

    @GetMapping
    public List<LegalCase> getAllCases() {
        return legalCaseService.getAllCases();
    }

    /**
     * CREATE CASE: The primary entry point for new dossiers.
     * Uses @RequestPart to handle the complex JSON + File upload simultaneously.
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createCase(
            @RequestPart("caseData") LegalCase legalCase,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {

        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Registry Access Denied: Auth Required.");
        }

        // 1. Secure File Handling
        if (file != null && !file.isEmpty()) {
            try {
                if (!Files.exists(root)) Files.createDirectories(root);

                // Use UUID to prevent filename collisions
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Files.copy(file.getInputStream(), this.root.resolve(filename));
                legalCase.setDocumentPath(filename);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body("Vault Storage Failure: " + e.getMessage());
            }
        }

        // 2. Metadata Assignment
        legalCase.setRegisteredBy(authentication.getName());
        if (legalCase.getStatus() == null) legalCase.setStatus("ACTIVE");

        // 3. Persistence & Auditing
        LegalCase savedCase = legalCaseService.saveCase(legalCase);

        auditLogService.logAction(
                "DOSSIER_INITIATED",
                authentication.getName(),
                "Created Case: " + savedCase.getCaseNumber()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(savedCase);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<LegalCase> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String newStatus = body.get("status");
        return legalCaseService.getCaseById(id).map(legalCase -> {
            String oldStatus = legalCase.getStatus();
            legalCase.setStatus(newStatus);
            LegalCase updated = legalCaseService.saveCase(legalCase);

            auditLogService.logAction("STATUS_CHANGE", auth.getName(),
                    "Case " + updated.getCaseNumber() + ": " + oldStatus + " -> " + newStatus);

            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archiveToVault(@PathVariable Long id, Authentication auth) {
        try {
            legalCaseService.softDeleteCase(id);
            auditLogService.logAction("VAULT_ARCHIVE", auth.getName(), "ID: " + id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}