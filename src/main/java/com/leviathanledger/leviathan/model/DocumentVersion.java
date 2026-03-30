package com.leviathanledger.leviathan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class DocumentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String filePath;

    @Column(nullable = false, updatable = false)
    private String fileHash; // The SHA-256 Fingerprint

    private Integer versionNumber;
    private LocalDateTime uploadedAt;
    private String uploadedBy; // References the Lawyer/Clerk ID

    @ManyToOne
    @JoinColumn(name = "case_id")
    private LegalCase legalCase;
}