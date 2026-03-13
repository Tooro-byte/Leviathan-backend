package com.leviathanledger.leviathan.model;

/**
 * Defines the access levels within the Leviathan Ledger system.
 * Each role maps to specific authorities within the Security Filter Chain.
 */
public enum ERole {
    ROLE_CLERK("Clerk"),
    ROLE_LAWYER("Lawyer"),
    ROLE_ADMIN("Administrator");

    private final String displayName;

    ERole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}