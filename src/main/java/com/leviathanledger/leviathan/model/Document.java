package com.leviathanledger.leviathan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * LexTracker Integrity Engine: Document Entity
 * Purpose: Acts as the "Vault Slot" for legal evidence with immutable hashing.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String fileType;

    @Column(nullable = false)
    private String filePath;

    @Column(nullable = false, unique = true)
    private String fileHash; // The SHA-256 Digital Fingerprint

    @Column(name = "source_origin")
    private String sourceOrigin; // Captured from the physical world origin

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_case_id")
    private LegalCase legalCase;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.version == null) {
            this.version = 1;
        }
    }

    public Document() {}

    public Document(String fileName, String fileType, String filePath, String fileHash, String sourceOrigin, String uploadedBy, LegalCase legalCase) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.fileHash = fileHash;
        this.sourceOrigin = sourceOrigin;
        this.uploadedBy = uploadedBy;
        this.legalCase = legalCase;
    }

    // --- GETTERS AND SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getSourceOrigin() { return sourceOrigin; }
    public void setSourceOrigin(String sourceOrigin) { this.sourceOrigin = sourceOrigin; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LegalCase getLegalCase() { return legalCase; }
    public void setLegalCase(LegalCase legalCase) { this.legalCase = legalCase; }
}