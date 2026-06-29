package com.example.inventoryservice.service;

import com.example.inventoryservice.model.ProcessedOrder;
import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProcessedOrderRepository;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ProductRepository productRepository;
    private final ProcessedOrderRepository processedOrderRepository;

    public ReservationService(ProductRepository productRepository,
                              ProcessedOrderRepository processedOrderRepository) {
        this.productRepository = productRepository;
        this.processedOrderRepository = processedOrderRepository;
    }

    @Transactional
    public Optional<InventoryReservationEvent> reserve(OrderPlacedEvent order) {
        String orderId = order.orderId();

        if (processedOrderRepository.existsById(orderId)) {
            log.info("Order {} already processed — no-op (outcome-idempotent)", orderId);
            return Optional.empty();
        }

        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElse(null);
            if (product == null || product.getQuantityOnHand() < item.quantity()) {
                InventoryReservationEvent rejected = new InventoryReservationEvent(
                        orderId,
                        InventoryReservationEvent.ReservationStatus.REJECTED,
                        "Insufficient stock for " + item.sku(),
                        Instant.now()
                );
                processedOrderRepository.save(new ProcessedOrder(orderId));
                return Optional.of(rejected);
            }
        }

        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElseThrow();
            product.setQuantityOnHand(product.getQuantityOnHand() - item.quantity());
            productRepository.save(product);
        }

        processedOrderRepository.save(new ProcessedOrder(orderId));

        return Optional.of(new InventoryReservationEvent(
                orderId,
                InventoryReservationEvent.ReservationStatus.RESERVED,
                null,
                Instant.now()
        ));
    }
}
