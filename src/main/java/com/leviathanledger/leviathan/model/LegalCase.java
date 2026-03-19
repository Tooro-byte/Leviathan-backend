package com.leviathanledger.leviathan.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "legal_cases")
@SQLRestriction("is_deleted = false")
public class LegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String caseNumber;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status = "PENDING"; // PENDING, DISCOVERY, HEARING, JUDGMENT

    private String documentPath;

    // --- NEW CLIENT IDENTITY FIELDS ---
    private String clientName;

    @Column(nullable = false)
    private String clientEmail; // The "Golden Key" for dashboard access

    private String clientPhone;
    private String clientLocation;
    private String clientDob;
    private Integer clientAge;
    private String clientPhotoPath; // Store path to the uploaded image

    // --- FINANCIALS ---
    private Double balance = 0.0;

    /**
     * ALIGNMENT FIX: Maps 'registeredBy' to 'primaryCounsel' in JSON
     */
    @JsonProperty("primaryCounsel")
    @Column(updatable = false)
    private String registeredBy;

    /**
     * AUDIT LOGGING
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_audit_logs", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "log_entry")
    @OrderColumn(name = "log_order")
    private List<String> auditLogs = new ArrayList<>();

    private LocalDateTime filedAt = LocalDateTime.now();

    private boolean isDeleted = false;

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public LegalCase() {}

    /**
     * Logic to auto-log status changes with a timestamp.
     */
    public void setStatus(String newStatus) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        if (this.status != null && !this.status.equals(newStatus)) {
            this.auditLogs.add("⚖️ Status transitioned from " + this.status + " to " + newStatus + " [" + timestamp + "]");
        } else if (this.status == null) {
            this.auditLogs.add("🚀 Case Initialized for Client: " + this.clientName + " [" + timestamp + "]");
        }
        this.status = newStatus;
    }

    public void addManualLog(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        this.auditLogs.add(message + " [" + timestamp + "]");
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }

    public String getDocumentPath() { return documentPath; }
    public void setDocumentPath(String documentPath) { this.documentPath = documentPath; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }

    public String getClientLocation() { return clientLocation; }
    public void setClientLocation(String clientLocation) { this.clientLocation = clientLocation; }

    public String getClientDob() { return clientDob; }
    public void setClientDob(String clientDob) { this.clientDob = clientDob; }

    public Integer getClientAge() { return clientAge; }
    public void setClientAge(Integer clientAge) { this.clientAge = clientAge; }

    public String getClientPhotoPath() { return clientPhotoPath; }
    public void setClientPhotoPath(String clientPhotoPath) { this.clientPhotoPath = clientPhotoPath; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }

    public List<String> getAuditLogs() { return auditLogs; }
    public void setAuditLogs(List<String> auditLogs) { this.auditLogs = auditLogs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}