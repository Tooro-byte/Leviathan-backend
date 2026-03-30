package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class LegalCaseService {

    @Autowired
    private LegalCaseRepository repository;

    /**
     * Finds a case by ID. Returns Optional for better error handling.
     */
    public Optional<LegalCase> getCaseById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id);
    }

    /**
     * Finds a case by ID and throws exception if not found.
     * Use this when you're sure the case exists.
     */
    public LegalCase getCaseByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Case not found with ID: " + id));
    }

    /**
     * Retrieves a case using the Client Email registered by the lawyer.
     */
    public Optional<LegalCase> getCaseByClientEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        return repository.findByClientEmailAndIsDeletedFalse(email);
    }

    /**
     * Retrieves a case using the Client's User ID (more reliable).
     */
    public Optional<LegalCase> getCaseByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findByClient_IdAndIsDeletedFalse(userId);
    }

    /**
     * Get case by client email or return null.
     */
    public LegalCase getCaseByClientEmailOrNull(String email) {
        return getCaseByClientEmail(email).orElse(null);
    }

    /**
     * Save or update a case.
     */
    @Transactional
    public LegalCase saveCase(LegalCase legalCase) {
        if (legalCase == null) {
            throw new IllegalArgumentException("LegalCase cannot be null");
        }

        if (legalCase.getCaseNumber() == null || legalCase.getCaseNumber().trim().isEmpty()) {
            legalCase.setCaseNumber(generateUgandanCaseId());
        }

        return repository.save(legalCase);
    }

    /**
     * Get all non-deleted cases.
     */
    public List<LegalCase> getAllCases() {
        return repository.findAll();
    }

    /**
     * Soft delete a case (archive it).
     */
    @Transactional
    public LegalCase softDeleteCase(Long id) {
        LegalCase caseToHide = getCaseByIdOrThrow(id);
        caseToHide.setDeleted(true);
        caseToHide.addManualLog("CASE ARCHIVED: This record has been moved to the secure vault.");
        return repository.save(caseToHide);
    }

    /**
     * Handle secure evidence attachment from the Client Portal.
     */
    @Transactional
    public void attachEvidence(Long caseId, MultipartFile file) throws IOException {
        if (caseId == null) {
            throw new IllegalArgumentException("Case ID cannot be null");
        }

        LegalCase legalCase = getCaseByIdOrThrow(caseId);

        String uploadDir = System.getProperty("user.dir") + "/uploads/evidence/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = "CLIENT_EVIDENCE_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        file.transferTo(new File(uploadDir + fileName));

        legalCase.setDocumentPath(fileName);
        legalCase.addManualLog("CLIENT UPLOAD: Secure evidence vaulted: " + file.getOriginalFilename());

        repository.save(legalCase);
    }

    /**
     * Generate a unique Ugandan case ID.
     */
    private String generateUgandanCaseId() {
        int currentYear = LocalDate.now().getYear();
        int randomNum = new Random().nextInt(900000) + 100000;
        return "LEX-UG-" + currentYear + "-" + randomNum + "-KLA";
    }
}