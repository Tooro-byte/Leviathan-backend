package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.time.LocalDate;

@Service
public class LegalCaseService {

    @Autowired
    private LegalCaseRepository repository;

    public Optional<LegalCase> getCaseById(Long id) {
        return repository.findById(id);
    }

    /**
     * Retrieves a case using the Client Email registered by the lawyer.
     */
    public Optional<LegalCase> getCaseByClientEmail(String email) {
        return repository.findByClientEmailAndIsDeletedFalse(email);
    }

    public LegalCase saveCase(LegalCase legalCase) {
        if (legalCase.getCaseNumber() == null || legalCase.getCaseNumber().trim().isEmpty()) {
            legalCase.setCaseNumber(generateUgandanCaseId());
        }
        return repository.save(legalCase);
    }

    public List<LegalCase> getAllCases() {
        return repository.findAll();
    }

    public LegalCase softDeleteCase(Long id) {
        LegalCase caseToHide = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        caseToHide.setDeleted(true);
        return repository.save(caseToHide);
    }

    /**
     * Handles secure evidence attachment from the Client Portal.
     */
    public void attachEvidence(Long caseId, MultipartFile file) throws IOException {
        LegalCase legalCase = repository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        String uploadDir = System.getProperty("user.dir") + "/uploads/evidence/";
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        String fileName = "CLIENT_EVIDENCE_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        file.transferTo(new File(uploadDir + fileName));

        // Update the case record and add to the Audit Shield
        legalCase.setDocumentPath(fileName);
        legalCase.addManualLog("CLIENT UPLOAD: Secure evidence vaulted: " + file.getOriginalFilename());

        repository.save(legalCase);
    }

    private String generateUgandanCaseId() {
        int currentYear = LocalDate.now().getYear();
        int randomNum = new Random().nextInt(900000) + 100000;
        return "LEX-UG-" + currentYear + "-" + randomNum + "-KLA";
    }
}