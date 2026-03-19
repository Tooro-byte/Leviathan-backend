package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.RoleRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
import com.leviathanledger.leviathan.security.JwtUtils;
import com.leviathanledger.leviathan.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@RequestBody User loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("accessToken", jwt);
        response.put("id", userDetails.getId());
        response.put("username", userDetails.getUsername());
        response.put("email", userDetails.getEmail());
        response.put("roles", roles);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody User signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already in use!"));
        }

        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        String strRole = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRole == null) {
            Role lawyerRole = roleRepository.findByName(ERole.ROLE_LAWYER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(lawyerRole);
        } else {
            switch (strRole.toUpperCase()) {
                case "CLERK":
                    Role clerkRole = roleRepository.findByName(ERole.ROLE_CLERK)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    roles.add(clerkRole);
                    break;
                case "CLIENT":
                    Role clientRole = roleRepository.findByName(ERole.ROLE_CLIENT)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    roles.add(clientRole);
                    break;
                default:
                    Role lawyerRole = roleRepository.findByName(ERole.ROLE_LAWYER)
                            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                    roles.add(lawyerRole);
            }
        }

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User registered successfully as " + (strRole != null ? strRole : "LAWYER")));
    }
}