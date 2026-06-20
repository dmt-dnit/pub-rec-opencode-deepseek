package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.publisher.OrderEventPublisher;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.PriceCatalog;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final PriceCatalog priceCatalog;
    private final OrderEventPublisher orderEventPublisher;

    public OrderController(OrderRepository orderRepository,
                           PriceCatalog priceCatalog,
                           OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.priceCatalog = priceCatalog;
        this.orderEventPublisher = orderEventPublisher;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        String customerEmail = jwt != null ? jwt.getSubject() : "anonymous";
        log.info("Order placement requested by: {}", customerEmail);

        for (OrderRequest.Item item : request.items()) {
            if (!priceCatalog.exists(item.sku())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown SKU: " + item.sku());
            }
        }

        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderLineItem> lineItems = new java.util.ArrayList<>();

        for (OrderRequest.Item item : request.items()) {
            BigDecimal unitPrice = priceCatalog.getPrice(item.sku()).orElseThrow();
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(item.quantity())));
            lineItems.add(new OrderLineItem(item.sku(), item.quantity()));
        }

        Order order = new Order(orderId, customerEmail, lineItems, totalAmount,
                OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        List<OrderItem> eventItems = request.items().stream()
                .map(i -> new OrderItem(i.sku(), i.quantity()))
                .toList();
        OrderPlacedEvent event = new OrderPlacedEvent(orderId, customerEmail, eventItems,
                totalAmount, now);
        orderEventPublisher.publish(event);

        log.info("Order placed: orderId={}, total={}", orderId, totalAmount);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .sorted((a, b) -> b.placedAt().compareTo(a.placedAt()))
                .toList();
    }

    public record OrderRequest(List<Item> items) {
        public record Item(String sku, int quantity) {}
    }
}