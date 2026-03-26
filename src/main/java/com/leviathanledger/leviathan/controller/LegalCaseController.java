package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.service.LegalCaseService;
import com.leviathanledger.leviathan.service.AuditLogService;
import com.leviathanledger.leviathan.repository.UserRepository;
import com.leviathanledger.leviathan.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cases")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class LegalCaseController {

    @Autowired
    private LegalCaseService legalCaseService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserRepository userRepository;

    private final Path root = Paths.get("uploads");

    @GetMapping
    public List<LegalCase> getAllCases() {
        return legalCaseService.getAllCases();
    }

    /**
     * NEW: Get case for authenticated client by User ID (most reliable)
     * This is the primary endpoint for client dashboard
     */
    @GetMapping("/my-case")
    public ResponseEntity<?> getMyCase(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            System.out.println("=== AUTH FAILED: No authentication ===");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        System.out.println("=== CLIENT USER ID FROM AUTH: " + userId + " ===");

        Optional<LegalCase> clientCase = legalCaseService.getCaseByUserId(userId);

        if (clientCase.isEmpty()) {
            System.out.println("=== NO CASE FOUND FOR USER ID: " + userId + " ===");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No case found for this client"));
        }

        System.out.println("=== CASE FOUND: " + clientCase.get().getCaseNumber() + " ===");
        return ResponseEntity.ok(clientCase.get());
    }

    /**
     * Backward compatibility: Get case by email (legacy endpoint)
     * Still works but recommended to use /my-case instead
     */
    @GetMapping("/client")
    public ResponseEntity<?> getClientCaseByEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }

        String clientEmail = authentication.getName();
        System.out.println("=== CLIENT EMAIL FROM AUTH (legacy): " + clientEmail + " ===");

        Optional<LegalCase> clientCase = legalCaseService.getCaseByClientEmail(clientEmail);

        if (clientCase.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No case found for this client"));
        }

        return ResponseEntity.ok(clientCase.get());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalCase> getCaseById(@PathVariable Long id) {
        return legalCaseService.getCaseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new case (with optional file attachment)
     * Automatically links client by email to get user_id
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createCase(
            @RequestPart("caseData") LegalCase legalCase,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Registry Access Denied: Auth Token Missing.");
        }

        // Link client by email to get user_id
        if (legalCase.getClientEmail() != null && !legalCase.getClientEmail().isEmpty()) {
            Optional<User> clientUser = userRepository.findByEmail(legalCase.getClientEmail());
            if (clientUser.isPresent()) {
                legalCase.setClient(clientUser.get());
                System.out.println("=== Linked case to client ID: " + clientUser.get().getId() +
                        " for email: " + legalCase.getClientEmail() + " ===");
            } else {
                System.out.println("=== WARNING: Client email not found in users table: " +
                        legalCase.getClientEmail() + " ===");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Client email does not exist. Please register the client first."));
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Client email is required to create a case"));
        }

        if (file != null && !file.isEmpty()) {
            try {
                if (!Files.exists(root)) {
                    Files.createDirectories(root);
                }
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Files.copy(file.getInputStream(), this.root.resolve(filename));
                legalCase.setDocumentPath(filename);
            } catch (Exception e) {
                return ResponseEntity.internalServerError()
                        .body("Vault Storage Failure: " + e.getMessage());
            }
        }

        legalCase.setRegisteredBy(authentication.getName());
        if (legalCase.getStatus() == null) {
            legalCase.setStatus("ACTIVE");
        }

        LegalCase savedCase = legalCaseService.saveCase(legalCase);

        auditLogService.logAction(
                "DOSSIER_INITIATED",
                authentication.getName(),
                "Created Case: " + savedCase.getCaseNumber() + " for client: " + savedCase.getClientEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(savedCase);
    }

    /**
     * Add manual audit entry (Audit Concierge)
     */
    @PostMapping("/{id}/audit")
    public ResponseEntity<LegalCase> addAuditEntry(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication auth) {

        String note = request.get("note");
        if (note == null || note.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return legalCaseService.getCaseById(id).map(legalCase -> {
            legalCase.addManualLog(note);
            LegalCase updated = legalCaseService.saveCase(legalCase);
            auditLogService.logAction("MANUAL_AUDIT", auth.getName(), note);
            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update case status
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<LegalCase> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String newStatus = body.get("status");
        if (newStatus == null) {
            return ResponseEntity.badRequest().build();
        }

        return legalCaseService.getCaseById(id).map(legalCase -> {
            String oldStatus = legalCase.getStatus();
            legalCase.setStatus(newStatus);
            LegalCase updated = legalCaseService.saveCase(legalCase);

            auditLogService.logAction("STATUS_CHANGE", auth.getName(),
                    "Case " + updated.getCaseNumber() + ": " + oldStatus + " -> " + newStatus);

            return ResponseEntity.ok(updated);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Soft delete / archive case
     */
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