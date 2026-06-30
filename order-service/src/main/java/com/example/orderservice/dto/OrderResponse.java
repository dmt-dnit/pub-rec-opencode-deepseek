package com.example.orderservice.dto;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerEmail,
        List<LineItem> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant placedAt,
        Instant updatedAt,
        String correlationId
) {
    public record LineItem(String sku, int quantity) {}

    /** Used for list queries where no correlation ID is in context. */
    public static OrderResponse from(Order order) {
        return from(order, null);
    }

    /** Used for saga completion where the correlation ID is available in the SLF4J MDC. */
    public static OrderResponse from(Order order, String correlationId) {
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerEmail(),
                order.getItems().stream()
                        .map(i -> new LineItem(i.getSku(), i.getQuantity()))
                        .toList(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getUpdatedAt(),
                correlationId
        );
    }
}
