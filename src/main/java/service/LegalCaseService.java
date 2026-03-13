package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.time.LocalDate;

@Service
public class LegalCaseService {

    @Autowired
    private LegalCaseRepository repository;

    /**
     * Finds a single case by its database ID.
     * This is the "Missing Link" that allows the Dossier to load.
     */
    public Optional<LegalCase> getCaseById(Long id) {
        return repository.findById(id);
    }

    /**
     * Saves a case and ensures a system-generated ID is applied.
     */
    public LegalCase saveCase(LegalCase legalCase) {
        if (legalCase.getCaseNumber() == null || legalCase.getCaseNumber().trim().isEmpty()) {
            legalCase.setCaseNumber(generateUgandanCaseId());
        }
        return repository.save(legalCase);
    }

    public List<LegalCase> getAllCases() {
        // Returns all cases.
        // Note: You can add repository.findByIsDeletedFalse() here later if needed.
        return repository.findAll();
    }

    public LegalCase softDeleteCase(Long id) {
        LegalCase caseToHide = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        caseToHide.setDeleted(true);
        return repository.save(caseToHide);
    }

    /**
     * LEX-UG-[YEAR]-[RANDOM]-[OFFICE]
     */
    private String generateUgandanCaseId() {
        int currentYear = LocalDate.now().getYear();
        int randomNum = new Random().nextInt(900000) + 100000;
        return "LEX-UG-" + currentYear + "-" + randomNum + "-KLA";
    }
}