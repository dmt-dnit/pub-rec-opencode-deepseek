package be.dnit.inventoryservice.repository;

import be.dnit.inventoryservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}
