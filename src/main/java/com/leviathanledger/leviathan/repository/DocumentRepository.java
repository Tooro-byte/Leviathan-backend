package com.leviathanledger.leviathan.repository;

import com.leviathanledger.leviathan.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByLegalCaseId(Long caseId);
    List<Document> findByLegalCaseIdAndArchivedFalse(Long caseId);
    List<Document> findByLegalCaseIdAndDocumentCategory(Long caseId, String category);
    Optional<Document> findByFileHash(String s);
}