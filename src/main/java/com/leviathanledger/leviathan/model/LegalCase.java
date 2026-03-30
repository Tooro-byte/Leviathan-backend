package com.leviathanledger.leviathan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String caseNumber;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status = "PENDING";

    private String documentPath;

    // --- CLIENT IDENTITY FIELDS ---
    private String clientName;
    private String clientEmail;
    private String clientPhone;
    private String clientLocation;
    private String clientDob;
    private Integer clientAge;
    private String clientPhotoPath;

    // --- DEFENDANT & COURT DETAILS (For PDF Generation) ---
    private String defendantName;
    private String defendantAddress;
    private String defendantEmail;
    private String courtLocation = "Kampala";
    private String division;
    private String caseSubject;

    // Link to User entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    @JsonIgnore
    private User client;

    // Court hearing tracking
    private LocalDateTime nextHearingDate;
    private Integer caseStage = 0;

    // Financials
    private Double balance = 0.0;

    @JsonProperty("primaryCounsel")
    @Column(updatable = false)
    private String registeredBy;

    // Audit Logging
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_audit_logs", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "log_entry")
    @OrderColumn(name = "log_order")
    private List<String> auditLogs = new ArrayList<>();

    private LocalDateTime filedAt = LocalDateTime.now();
    private boolean isDeleted = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    public LegalCase() {}

    // --- CUSTOM LOGIC METHODS ---

    /**
     * Custom setter for status that automatically logs the change.
     */
    public void setStatus(String newStatus) {
        if (newStatus != null && !newStatus.equals(this.status)) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            this.auditLogs.add("⚖️ Status transitioned from " + this.status + " to " + newStatus + " [" + timestamp + "]");
        }
        this.status = newStatus;
    }

    /**
     * Add a manual audit log entry.
     */
    public void addManualLog(String message) {
        if (message != null && !message.trim().isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            this.auditLogs.add(message + " [" + timestamp + "]");
        }
    }

    // --- GETTERS AND SETTERS ---

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

    // Client fields
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

    // Defendant fields
    public String getDefendantName() { return defendantName; }
    public void setDefendantName(String defendantName) { this.defendantName = defendantName; }

    public String getDefendantAddress() { return defendantAddress; }
    public void setDefendantAddress(String defendantAddress) { this.defendantAddress = defendantAddress; }

    public String getDefendantEmail() { return defendantEmail; }
    public void setDefendantEmail(String defendantEmail) { this.defendantEmail = defendantEmail; }

    // Court fields
    public String getCourtLocation() { return courtLocation; }
    public void setCourtLocation(String courtLocation) { this.courtLocation = courtLocation; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getCaseSubject() { return caseSubject; }
    public void setCaseSubject(String caseSubject) { this.caseSubject = caseSubject; }

    // User relationship
    public User getClient() { return client; }
    public void setClient(User client) { this.client = client; }

    // Hearing tracking
    public LocalDateTime getNextHearingDate() { return nextHearingDate; }
    public void setNextHearingDate(LocalDateTime nextHearingDate) { this.nextHearingDate = nextHearingDate; }

    public Integer getCaseStage() { return caseStage; }
    public void setCaseStage(Integer caseStage) { this.caseStage = caseStage; }

    // Financials
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    // Audit
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }

    public List<String> getAuditLogs() { return auditLogs; }
    public void setAuditLogs(List<String> auditLogs) { this.auditLogs = auditLogs; }

    // Timestamps
    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}