package be.dnit.inventoryservice.repository;

import be.dnit.inventoryservice.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
}
