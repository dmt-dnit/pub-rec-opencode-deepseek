package be.dnit.orderservice;

import be.dnit.orderservice.dto.OrderResponse;
import be.dnit.orderservice.model.Order;
import be.dnit.orderservice.model.OrderLineItem;
import be.dnit.orderservice.model.OrderStatus;
import be.dnit.orderservice.repository.OrderRepository;
import be.dnit.sharedmodel.InventoryReservationEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-inv-events-idem"})
@ActiveProfiles("test")
@DirtiesContext
class InventoryReservationListenerIdempotencyTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-order-events-idem");
        registry.add("app.kafka.listen-topic", () -> "test-inv-events-idem");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void shouldTransitionOrderOnlyOnceOnDuplicateEvent() throws Exception {
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Order order = new Order(orderId, "customer@example.test",
                List.of(new OrderLineItem("SKU-001", 1)),
                new BigDecimal("9.99"), OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        InventoryReservationEvent event = new InventoryReservationEvent(
                orderId, InventoryReservationEvent.ReservationStatus.RESERVED,
                null, Instant.now());

        kafkaTemplate.send("test-inv-events-idem", orderId, event);

        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            Order updated = orderRepository.findById(orderId).orElseThrow();
            if (updated.getStatus() == OrderStatus.CONFIRMED) {
                break;
            }
        }

        kafkaTemplate.send("test-inv-events-idem", orderId, event);

        Thread.sleep(3000);

        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<OrderResponse> captor = ArgumentCaptor.forClass(OrderResponse.class);
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/messages"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
