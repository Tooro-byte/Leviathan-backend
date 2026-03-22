package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.AuditLog;
import com.leviathanledger.leviathan.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void logAction(String action, String username, String details) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUsername(username);
        log.setDetails(details);

        // --- SAFE REQUEST RETRIEVAL ---
        String remoteAddr = "0.0.0.0";
        String userAgent = "UNKNOWN_DEVICE";

        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // Capture IP Address (handling proxies)
                remoteAddr = request.getHeader("X-Forwarded-For");
                if (remoteAddr == null || remoteAddr.isEmpty()) {
                    remoteAddr = request.getRemoteAddr();
                }

                // Capture Device Info
                userAgent = request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // Fallback if request context is unreachable (common in async/complex transactions)
            remoteAddr = "SYSTEM_INTERNAL";
        }

        log.setIpAddress(remoteAddr);
        log.setDeviceFingerprint(userAgent);

        auditLogRepository.save(log);
    }
}