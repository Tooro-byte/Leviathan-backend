package com.leviathanledger.leviathan.repository;

import com.leviathanledger.leviathan.model.LegalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {

    /**
     * Finds a case by the client's registered email.
     * Used for Client Dashboard authentication and lookup.
     */
    Optional<LegalCase> findByClientEmailAndIsDeletedFalse(String clientEmail);

    /**
     * NEW: Finds a case by the client's user ID (more reliable)
     * Used for client dashboard to fetch case by authenticated user ID
     */
    Optional<LegalCase> findByClient_IdAndIsDeletedFalse(Long userId);

    /**
     * Checks if a case already exists for this email to prevent duplicates.
     */
    boolean existsByClientEmailAndIsDeletedFalse(String clientEmail);
}