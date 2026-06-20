package com.example.inventoryservice.service;

import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReservationService {

    private final ProductRepository productRepository;

    public ReservationService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public InventoryReservationEvent reserve(OrderPlacedEvent order) {
        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElse(null);
            if (product == null || product.getQuantityOnHand() < item.quantity()) {
                return new InventoryReservationEvent(
                        order.orderId(),
                        InventoryReservationEvent.ReservationStatus.REJECTED,
                        "Insufficient stock for " + item.sku(),
                        Instant.now()
                );
            }
        }

        for (OrderItem item : order.items()) {
            Product product = productRepository.findById(item.sku()).orElseThrow();
            product.setQuantityOnHand(product.getQuantityOnHand() - item.quantity());
            productRepository.save(product);
        }

        return new InventoryReservationEvent(
                order.orderId(),
                InventoryReservationEvent.ReservationStatus.RESERVED,
                null,
                Instant.now()
        );
    }
}
