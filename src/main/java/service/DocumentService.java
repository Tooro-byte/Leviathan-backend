package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.repository.DocumentRepository;
import com.leviathanledger.leviathan.repository.LegalCaseRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@Service
public class DocumentService {

    @Autowired private DocumentRepository documentRepository;
    @Autowired private LegalCaseRepository legalCaseRepository;
    @Autowired private com.leviathanledger.leviathan.service.FileStorageService fileStorageService;
    @Autowired private com.leviathanledger.leviathan.service.AuditLogService auditLogService;

    public Document processUpload(MultipartFile file, Long caseId, String username, HttpServletRequest request) throws Exception {
        LegalCase legalCase = legalCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Case not found"));

        String hash = fileStorageService.calculateHash(file);
        String savedPath = fileStorageService.saveToDisk(file);

        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFilePath(savedPath);
        doc.setFileHash(hash);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setUploadedBy(username);
        doc.setLegalCase(legalCase);

        Document savedDoc = documentRepository.save(doc);

        // Integrate with Phase 1 Audit Logs
        auditLogService.logAction(
                "DOCUMENT_UPLOADED",
                username,
                "Uploaded file: " + file.getOriginalFilename() + " [Hash: " + hash + "]",
                request
        );

        return savedDoc;
    }
}