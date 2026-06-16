package com.example.authserver;

import com.example.authserver.model.UserEntity;
import com.example.authserver.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        UserEntity admin = new UserEntity(
                "admin@example.com",
                passwordEncoder.encode("admin123"),
                "Admin User",
                UserEntity.Role.ADMIN,
                UserEntity.Status.ACTIVE
        );
        userRepository.save(admin);

        UserEntity user = new UserEntity(
                "user@example.com",
                passwordEncoder.encode("user123"),
                "Test User",
                UserEntity.Role.USER,
                UserEntity.Status.ACTIVE
        );
        userRepository.save(user);

        System.out.println("--- SEEDED USERS ---");
        System.out.println("Admin: admin@example.com / admin123");
        System.out.println("User:  user@example.com / user123");
    }
}
