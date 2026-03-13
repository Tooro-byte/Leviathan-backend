package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.service.LegalCaseService;
import com.leviathanledger.leviathan.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
public class LegalCaseController {

    @Autowired private LegalCaseService legalCaseService;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public List<LegalCase> getAllCases() {
        return legalCaseService.getAllCases();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LegalCase> getCaseById(@PathVariable Long id) {
        return legalCaseService.getCaseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // NEW: Update Status & Auto-Log
    @PutMapping("/{id}/status")
    public ResponseEntity<LegalCase> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");
        return legalCaseService.getCaseById(id).map(legalCase -> {
            legalCase.setStatus(newStatus); // This triggers the model's audit log logic
            return ResponseEntity.ok(legalCaseService.saveCase(legalCase));
        }).orElse(ResponseEntity.notFound().build());
    }

    // NEW: Manual Audit Entry
    @PostMapping("/{id}/audit")
    public ResponseEntity<LegalCase> addManualAudit(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        String note = body.get("note");
        return legalCaseService.getCaseById(id).map(legalCase -> {
            legalCase.addManualLog(auth.getName() + ": " + note);
            return ResponseEntity.ok(legalCaseService.saveCase(legalCase));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createCase(@RequestPart("caseData") LegalCase legalCase,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        HttpServletRequest request,
                                        Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (file != null && !file.isEmpty()) {
            try {
                String uploadDir = System.getProperty("user.dir") + "/uploads/";
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                file.transferTo(new File(uploadDir + fileName));
                legalCase.setDocumentPath(fileName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
            }
        }

        // Set the initial counsel name from the logged-in user
        legalCase.setRegisteredBy(authentication.getName());

        LegalCase savedCase = legalCaseService.saveCase(legalCase);
        auditLogService.logAction("CASE_CREATED", authentication.getName(), "ID: " + savedCase.getCaseNumber(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCase);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCase(@PathVariable Long id, HttpServletRequest request, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        LegalCase deleted = legalCaseService.softDeleteCase(id);
        auditLogService.logAction("CASE_SOFT_DELETED", authentication.getName(), "Vaulted: " + deleted.getCaseNumber(), request);
        return ResponseEntity.ok("Case moved to vault.");
    }
}