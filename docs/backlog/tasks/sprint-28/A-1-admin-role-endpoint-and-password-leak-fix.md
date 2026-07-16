# A-1 — auth-server: role-promotion endpoint + stop leaking password hashes

**Sprint:** 28. **Scope:** `auth-server` only, 2 files.

## Current state (verified, re-check if it's moved)

`auth-server/src/main/java/be/dnit/authserver/controller/AdminController.java` (full
current content):
```java
package be.dnit.authserver.controller;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/users/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        UserEntity user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        user.setStatus(UserEntity.Status.ACTIVE);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "User approved", "email", user.getEmail()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }
}
```

`UserEntity` (`auth-server/src/main/java/be/dnit/authserver/model/UserEntity.java`) has
`public enum Role { CUSTOMER, WAREHOUSE_STAFF, ADMIN }` and a plain `private String
password;` field with a public getter/setter, no serialization annotation — this is why
`listUsers()` currently returns the bcrypt hash in the JSON response body (verified live:
`GET /api/admin/users` returns a `"password": "$2a$10$..."` field for every user).

## What to change

### 1. Stop serializing the password hash

Add `@com.fasterxml.jackson.annotation.JsonIgnore` on `UserEntity`'s `password` field (or
its getter — either works, pick whichever matches this codebase's existing annotation
placement conventions if any exist; if none exist, field-level is fine). This project is
on Spring Boot 4.1 with real Jackson 3 (Sprint 16's SB-3), but `jackson-annotations`
(where `@JsonIgnore` lives) kept the `com.fasterxml.jackson.annotation` package even in
Jackson 3 — **verify this compiles**, don't assume it from this note.

Do not change the `password` field's type, JPA mapping, or the getter/setter signatures
— `CustomUserDetailsService`/`AuthController` likely still need to read/write it
internally for login and registration. Only the JSON *serialization* should change.

### 2. New role-promotion endpoint

Add to `AdminController`:
```java
public record RoleChangeRequest(String role) {}

@PutMapping("/users/{id}/role")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> changeUserRole(@PathVariable Long id, @RequestBody RoleChangeRequest request) {
    UserEntity user = userRepository.findById(id).orElse(null);
    if (user == null) {
        return ResponseEntity.notFound().build();
    }
    UserEntity.Role newRole;
    try {
        newRole = UserEntity.Role.valueOf(request.role());
    } catch (IllegalArgumentException | NullPointerException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + request.role()));
    }
    user.setRole(newRole);
    userRepository.save(user);
    return ResponseEntity.ok(Map.of("message", "Role updated", "email", user.getEmail(), "role", user.getRole().name()));
}
```
Adjust the exact shape/record placement to fit this file's actual conventions (e.g., if
records-as-nested-types in a controller isn't idiomatic here, a separate small DTO class
or file is fine — just keep the same validation behavior: reject any string that isn't
exactly `CUSTOMER`, `WAREHOUSE_STAFF`, or `ADMIN` with a 400, not a 500 or a silent
no-op).

## Explicitly out of scope

- No self-promotion guard, no "can't demote the last admin" check, no audit logging —
  see the sprint overview's "Explicitly out of scope." This is a minimal, correctly-
  validated endpoint, not a full admin-safety feature.
- Do not wire this endpoint into any frontend — it's API-only for now (A-2's UI only
  uses the existing `approve` endpoint).
- Do not touch `AuthController`, `SecurityConfig`, or any other file.

## Acceptance criteria (show real output, don't assert "Pass")

1. `cd shared-model && ./mvnw clean install`, then `auth-server`:
   `./mvnw clean verify` — **BUILD SUCCESS**, actual output shown.
2. Show the actual `git diff` of both changed files.
3. Confirm the password field no longer appears in a real response: this service uses
   in-memory H2 so there's no live DB to query directly from a test — if a test class
   exists that calls `GET /api/admin/users` (or a similar admin endpoint) as an admin
   user, run it and show the response body doesn't contain a `password` key. If no such
   test exists, state that explicitly rather than skipping the check — a coordinator
   will verify this live against the deployed environment separately.
4. `git status --short` clean after commit.
