package com.leviathanledger.leviathan.model;
public enum ERole {
    ROLE_CLERK("Clerk"),
    ROLE_LAWYER("Lawyer"),
    ROLE_CLIENT("Client"),
    ROLE_ADMIN("Administrator");

    private final String displayName;

    ERole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}