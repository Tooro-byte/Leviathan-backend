package com.leviathanledger.leviathan.service;

import com.leviathanledger.leviathan.model.AuditLog;
import com.leviathanledger.leviathan.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void logAction(String action, String username, String details, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setUsername(username);
        log.setDetails(details);

        // Capture IP Address (handling proxies)
        String remoteAddr = request.getHeader("X-Forwarded-For");
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            remoteAddr = request.getRemoteAddr();
        }
        log.setIpAddress(remoteAddr);

        // Capture Device Info (User-Agent)
        log.setDeviceFingerprint(request.getHeader("User-Agent"));

        auditLogRepository.save(log);
    }
}