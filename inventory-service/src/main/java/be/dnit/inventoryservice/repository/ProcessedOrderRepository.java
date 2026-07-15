package be.dnit.inventoryservice.repository;

import be.dnit.inventoryservice.model.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, String> {
}
