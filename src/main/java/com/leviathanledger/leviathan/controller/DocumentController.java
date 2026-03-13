package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.Document;
import com.leviathanledger.leviathan.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/upload/{caseId}")
    public ResponseEntity<?> uploadDocument(@PathVariable Long caseId,
                                            @RequestParam("file") MultipartFile file,
                                            HttpServletRequest request,
                                            Authentication auth) {
        try {
            Document doc = documentService.processUpload(file, caseId, auth.getName(), request);
            return ResponseEntity.ok(doc);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Chain of Custody Error: " + e.getMessage());
        }
    }
}