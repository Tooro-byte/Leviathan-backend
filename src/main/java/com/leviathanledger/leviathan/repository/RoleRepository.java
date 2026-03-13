package com.leviathanledger.leviathan.repository;

import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(ERole name);
}