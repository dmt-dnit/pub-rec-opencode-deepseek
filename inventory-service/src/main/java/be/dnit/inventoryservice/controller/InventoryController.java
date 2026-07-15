package be.dnit.inventoryservice.controller;

import be.dnit.inventoryservice.model.Product;
import be.dnit.inventoryservice.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ProductRepository productRepository;

    public InventoryController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Product> getAll() {
        return productRepository.findAll();
    }
}
