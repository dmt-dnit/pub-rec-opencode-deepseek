package be.dnit.sharedmodel;

import java.time.Instant;

public record InventoryReservationEvent(
        String orderId,
        ReservationStatus status,
        String reason,
        Instant processedAt
) {
    public enum ReservationStatus { RESERVED, REJECTED }
}
