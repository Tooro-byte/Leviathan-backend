package com.leviathanledger.leviathan.config;

import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.RoleRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder encoder;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedDefaultUsers();
    }

    private void seedRoles() {
        Arrays.stream(ERole.values()).forEach(roleEnum -> {
            if (roleRepository.findByName(roleEnum).isEmpty()) {
                Role newRole = new Role();
                newRole.setName(roleEnum);
                roleRepository.save(newRole);
                System.out.println("✅ Security Seed: " + roleEnum.name() + " has been initialized.");
            }
        });
    }

    private void seedDefaultUsers() {
        // 1. SEED LAWYER (The Master Account)
        if (!userRepository.existsByEmail("lawyer@leviathan.com")) {
            User lawyer = new User("Principal Lawyer", "lawyer@leviathan.com", encoder.encode("password123"));
            Set<Role> roles = new HashSet<>();
            roles.add(roleRepository.findByName(ERole.ROLE_LAWYER).get());
            lawyer.setRoles(roles);
            userRepository.save(lawyer);
            System.out.println("⚖️ Vault Primary: lawyer@leviathan.com initialized.");
        }

        // 2. SEED CLERK
        if (!userRepository.existsByEmail("clerk@registry.gov")) {
            User clerk = new User("Senior Clerk", "clerk@registry.gov", encoder.encode("clerkpass"));
            Set<Role> roles = new HashSet<>();
            roles.add(roleRepository.findByName(ERole.ROLE_CLERK).get());
            clerk.setRoles(roles);
            userRepository.save(clerk);
            System.out.println("📜 Registry Official: clerk@registry.gov initialized.");
        }

        System.out.println("🛡️ Leviathan Ledger: All Personas and Credentials Authorized.");
    }
}