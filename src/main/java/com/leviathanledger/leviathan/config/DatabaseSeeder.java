package com.leviathanledger.leviathan.config;
import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import com.leviathanledger.leviathan.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public DatabaseSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        seedRoles();
    }

    private void seedRoles() {
        // Loop through the professional ERole enum we refactored
        for (ERole roleName : ERole.values()) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role newRole = new Role();
                newRole.setName(roleName);
                roleRepository.save(newRole);
                System.out.println("Seeded role: " + roleName);
            }
        }
    }
}