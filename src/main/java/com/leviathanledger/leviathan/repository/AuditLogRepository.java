package com.leviathanledger.leviathan.repository;

import com.leviathanledger.leviathan.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // 1. Find all actions performed by a specific lawyer (e.g., richard_developer)
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    // 2. Find actions performed from a specific IP address (Anti-corruption check)
    List<AuditLog> findByIpAddressOrderByTimestampDesc(String ipAddress);

    // 3. Search for specific types of actions (e.g., "CASE_SOFT_DELETED")
    List<AuditLog> findByAction(String action);
}