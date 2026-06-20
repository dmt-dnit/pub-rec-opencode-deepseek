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
                "admin@example.test",
                passwordEncoder.encode("admin123"),
                "Admin User",
                UserEntity.Role.ADMIN,
                UserEntity.Status.ACTIVE
        );
        userRepository.save(admin);

        UserEntity customer = new UserEntity(
                "customer1@example.test",
                passwordEncoder.encode("customer123"),
                "Demo Customer",
                UserEntity.Role.CUSTOMER,
                UserEntity.Status.ACTIVE
        );
        userRepository.save(customer);

        UserEntity warehouse = new UserEntity(
                "warehouse1@example.test",
                passwordEncoder.encode("warehouse123"),
                "Demo Warehouse Staff",
                UserEntity.Role.WAREHOUSE_STAFF,
                UserEntity.Status.ACTIVE
        );
        userRepository.save(warehouse);

        System.out.println("--- SEEDED USERS ---");
        System.out.println("Admin:     admin@example.test / admin123");
        System.out.println("Customer:  customer1@example.test / customer123");
        System.out.println("Warehouse: warehouse1@example.test / warehouse123");
    }
}
