package com.example.authserver.controller;

import com.example.authserver.model.UserEntity;
import com.example.authserver.repository.UserRepository;
import com.example.authserver.service.JwtService;
import com.example.sharedmodel.LoginRequest;
import com.example.sharedmodel.LoginResponse;
import com.example.sharedmodel.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        UserEntity user = new UserEntity(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                UserEntity.Role.CUSTOMER,
                UserEntity.Status.PENDING
        );
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Registration submitted. Awaiting admin approval.",
                "email", request.email()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        if (user.getStatus() != UserEntity.Status.ACTIVE) {
            return ResponseEntity.status(403).body(Map.of("error", "Account not active. Awaiting admin approval."));
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, user.getEmail(), user.getName(), user.getRole().name()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "email", jwt.getSubject(),
                "name", jwt.getClaimAsString("name"),
                "role", jwt.getClaimAsString("role")
        ));
    }
}
