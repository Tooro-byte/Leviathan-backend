package com.leviathanledger.leviathan.config;

import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import com.leviathanledger.leviathan.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
 
/**
 * Ensures the Security Vault is primed with the necessary Persona Roles.
 * This runs automatically on startup.
 */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
    }

    private void seedRoles() {
        // We check each role individually to avoid duplicates or missing entries
        Arrays.stream(ERole.values()).forEach(roleEnum -> {
            if (roleRepository.findByName(roleEnum).isEmpty()) {
                Role newRole = new Role();
                newRole.setName(roleEnum);
                roleRepository.save(newRole);
                System.out.println("✅ Security Seed: " + roleEnum.name() + " has been initialized.");
            }
        });

        System.out.println("🛡️  Leviathan Ledger: All Personas are authorized in the database.");
    }
}