package be.dnit.sharedmodel;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: serialise + deserialise OrderPlacedEvent and
 * InventoryReservationEvent with the same Jackson 3 ObjectMapper that the
 * Kafka serde uses.  Asserting the *exact* JSON field names means that any
 * rename or removal on one side of the wire breaks this test before the
 * mismatch reaches a runtime environment.
 *
 * No Spring context, no Docker — runs everywhere as a fast unit test.
 */
class EventContractTest {

    // Plain Jackson 3 ObjectMapper — matches what JacksonJsonSerializer uses
    // under the hood (Spring Boot 4.1.0 / tools.jackson.core:jackson-databind:3.1.4).
    private final ObjectMapper mapper = new ObjectMapper();

    // -----------------------------------------------------------------------
    // OrderPlacedEvent
    // -----------------------------------------------------------------------

    @Test
    void orderPlacedEvent_exactFieldSet() throws Exception {
        var event = new OrderPlacedEvent(
                "order-123",
                "customer@example.test",
                List.of(new OrderItem("SKU-001", 2)),
                new BigDecimal("19.98"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        String json = mapper.writeValueAsString(event);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = mapper.readValue(json, Map.class);

        // Explicit field-name assertion: rename OR remove any component and this fails.
        assertThat(fields.keySet())
                .as("OrderPlacedEvent JSON must have exactly these fields")
                .containsExactlyInAnyOrder("orderId", "customerEmail", "items",
                        "totalAmount", "placedAt");
    }

    @Test
    void orderPlacedEvent_roundTrip() throws Exception {
        var event = new OrderPlacedEvent(
                "order-rt-1",
                "rt@example.test",
                List.of(new OrderItem("SKU-RT", 3)),
                new BigDecimal("9.99"),
                Instant.parse("2026-06-01T10:00:00Z")
        );

        String json = mapper.writeValueAsString(event);
        OrderPlacedEvent back = mapper.readValue(json, OrderPlacedEvent.class);

        assertThat(back.orderId()).isEqualTo(event.orderId());
        assertThat(back.customerEmail()).isEqualTo(event.customerEmail());
        assertThat(back.totalAmount()).isEqualByComparingTo(event.totalAmount());
        assertThat(back.items()).hasSize(1);
        assertThat(back.items().get(0).sku()).isEqualTo("SKU-RT");
        assertThat(back.items().get(0).quantity()).isEqualTo(3);
        assertThat(back.placedAt()).isEqualTo(event.placedAt());
    }

    // -----------------------------------------------------------------------
    // InventoryReservationEvent
    // -----------------------------------------------------------------------

    @Test
    void inventoryReservationEvent_exactFieldSet() throws Exception {
        var event = new InventoryReservationEvent(
                "order-456",
                InventoryReservationEvent.ReservationStatus.RESERVED,
                null,
                Instant.parse("2026-01-01T00:00:01Z")
        );

        String json = mapper.writeValueAsString(event);

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = mapper.readValue(json, Map.class);

        // Explicit field-name assertion — includes "reason" even when null so
        // removing the field from the record still fails this assertion.
        assertThat(fields.keySet())
                .as("InventoryReservationEvent JSON must have exactly these fields")
                .containsExactlyInAnyOrder("orderId", "status", "reason", "processedAt");
    }

    @Test
    void inventoryReservationEvent_roundTrip() throws Exception {
        var event = new InventoryReservationEvent(
                "order-rt-2",
                InventoryReservationEvent.ReservationStatus.REJECTED,
                "Out of stock",
                Instant.parse("2026-06-01T10:00:01Z")
        );

        String json = mapper.writeValueAsString(event);
        InventoryReservationEvent back = mapper.readValue(json, InventoryReservationEvent.class);

        assertThat(back.orderId()).isEqualTo(event.orderId());
        assertThat(back.status()).isEqualTo(InventoryReservationEvent.ReservationStatus.REJECTED);
        assertThat(back.reason()).isEqualTo("Out of stock");
        assertThat(back.processedAt()).isEqualTo(event.processedAt());
    }
}
