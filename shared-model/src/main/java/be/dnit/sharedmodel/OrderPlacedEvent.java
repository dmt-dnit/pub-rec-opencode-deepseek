package be.dnit.sharedmodel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderPlacedEvent(
        String orderId,
        String customerEmail,
        List<OrderItem> items,
        BigDecimal totalAmount,
        Instant placedAt
) {}
