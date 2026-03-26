package com.leviathanledger.leviathan.controller;

import com.leviathanledger.leviathan.model.ERole;
import com.leviathanledger.leviathan.model.Role;
import com.leviathanledger.leviathan.model.User;
import com.leviathanledger.leviathan.repository.RoleRepository;
import com.leviathanledger.leviathan.repository.UserRepository;
import com.leviathanledger.leviathan.security.UserDetailsImpl;
import com.leviathanledger.leviathan.security.JwtUtils;
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
        // Auth via Email as per LexTracker Frontend protocol
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

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

        // SAFE ROLE MAPPING: Preventing .get() crashes
        if (strRole == null) {
            roles.add(roleRepository.findByName(ERole.ROLE_LAWYER)
                    .orElseThrow(() -> new RuntimeException("Error: Default Role ROLE_LAWYER not found.")));
        } else {
            switch (strRole.toUpperCase()) {
                case "CLERK":
                    roles.add(roleRepository.findByName(ERole.ROLE_CLERK)
                            .orElseThrow(() -> new RuntimeException("Error: ROLE_CLERK not found.")));
                    break;
                case "CLIENT":
                    roles.add(roleRepository.findByName(ERole.ROLE_CLIENT)
                            .orElseThrow(() -> new RuntimeException("Error: ROLE_CLIENT not found.")));
                    break;
                default:
                    roles.add(roleRepository.findByName(ERole.ROLE_LAWYER)
                            .orElseThrow(() -> new RuntimeException("Error: ROLE_LAWYER not found.")));
            }
        }

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }
}