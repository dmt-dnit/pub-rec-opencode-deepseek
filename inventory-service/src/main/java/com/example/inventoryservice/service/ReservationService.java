package com.example.inventoryservice.service;

import com.example.inventoryservice.model.OutboxEvent;
import com.example.inventoryservice.model.ProcessedOrder;
import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.OutboxEventRepository;
import com.example.inventoryservice.repository.ProcessedOrderRepository;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ProductRepository productRepository;
    private final ProcessedOrderRepository processedOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String topic;

    public ReservationService(ProductRepository productRepository,
                              ProcessedOrderRepository processedOrderRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper,
                              @Value("${app.kafka.topic}") String topic) {
        this.productRepository = productRepository;
        this.processedOrderRepository = processedOrderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Transactional
    public void reserve(OrderPlacedEvent order) {
        String orderId = order.orderId();

        if (processedOrderRepository.existsById(orderId)) {
            log.info("Order {} already processed — no-op (outcome-idempotent)", orderId);
            return;
        }

        InventoryReservationEvent outcome;
        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElse(null);
            if (product == null || product.getQuantityOnHand() < item.quantity()) {
                outcome = new InventoryReservationEvent(
                        orderId,
                        InventoryReservationEvent.ReservationStatus.REJECTED,
                        "Insufficient stock for " + item.sku(),
                        Instant.now()
                );
                processedOrderRepository.save(new ProcessedOrder(orderId));
                writeOutbox(orderId, outcome);
                return;
            }
        }

        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElseThrow();
            product.setQuantityOnHand(product.getQuantityOnHand() - item.quantity());
            productRepository.save(product);
        }

        processedOrderRepository.save(new ProcessedOrder(orderId));

        outcome = new InventoryReservationEvent(
                orderId,
                InventoryReservationEvent.ReservationStatus.RESERVED,
                null,
                Instant.now()
        );
        writeOutbox(orderId, outcome);
    }

    private void writeOutbox(String orderId, InventoryReservationEvent outcome) {
        try {
            String payload = objectMapper.writeValueAsString(outcome);
            OutboxEvent outbox = new OutboxEvent(
                    orderId, payload, topic,
                    OutboxEvent.Status.PENDING, Instant.now()
            );
            outboxEventRepository.save(outbox);
            log.info("Wrote outbox event for orderId={} status={}", orderId, outcome.status());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox event for order " + orderId, e);
        }
    }
}
