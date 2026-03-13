package com.leviathanledger.leviathan.repository;

import com.leviathanledger.leviathan.model.LegalCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, Long> {
    // This gives you the power to .save(), .findAll(), and .delete()
}