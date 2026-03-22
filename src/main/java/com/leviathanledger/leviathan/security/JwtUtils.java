package com.leviathanledger.leviathan.security.jwt; // MATCHES EXPLORER: security/jwt

import com.leviathanledger.leviathan.security.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JwtUtils - The Leviathan Cryptographic Engine
 * STATUS: CLEAN - Sub-package Aligned
 * Purpose: Signs and validates the "Digital Shield" tokens.
 */
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    // GOLDEN SECRET: Hardcoded for session persistence across restarts
    private final String jwtSecret = "LeviathanLedger_Secure_Vault_Protocol_2026_Global_High_Court_Key_Final_32Chars";
    private final int jwtExpirationMs = 86400000; // 24 Hours

    /**
     * Generates a secure signing key from the secret string.
     */
    private Key getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates the JWT token using the UserDetails.
     */
    public String generateJwtToken(Authentication authentication) {
        // Cast to our custom UserDetails implementation
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject(userPrincipal.getEmail()) // Using Email as the unique identity
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Extracts the Identity (Email) from the token.
     */
    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Validates the token integrity and expiration.
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(authToken);
            return true;
        } catch (SignatureException e) {
            logger.error("🛡️ LEX-SECURITY: Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("🛡️ LEX-SECURITY: Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("🛡️ LEX-SECURITY: JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("🛡️ LEX-SECURITY: JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("🛡️ LEX-SECURITY: JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}