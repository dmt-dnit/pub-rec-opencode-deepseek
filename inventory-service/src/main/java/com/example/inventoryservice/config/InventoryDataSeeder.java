package com.example.inventoryservice.config;

import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class InventoryDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    public InventoryDataSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) return;

        productRepository.save(new Product("SKU-001", "Widget", 50));
        productRepository.save(new Product("SKU-002", "Gadget", 5));
        productRepository.save(new Product("SKU-003", "Gizmo", 0));
    }
}
