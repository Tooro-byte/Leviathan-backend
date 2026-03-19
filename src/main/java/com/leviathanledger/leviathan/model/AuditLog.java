package com.leviathanledger.leviathan.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * LexTracker Audit Ledger
 * Purpose: Provides a forensic trail of every "Certified" action in the system.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 1000)
    private String details;

    private String ipAddress;
    private String deviceFingerprint;

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(String action, String username, String details, String ipAddress, String deviceFingerprint) {
        this.action = action;
        this.username = username;
        this.details = details;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
        this.timestamp = LocalDateTime.now();
    }

    // --- GETTERS AND SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
}