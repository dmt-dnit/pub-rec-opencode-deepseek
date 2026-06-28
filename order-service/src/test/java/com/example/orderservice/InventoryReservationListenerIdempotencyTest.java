package com.example.orderservice;

import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-inv-events-idem"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9095", "port=9095"})
@ActiveProfiles("test")
@DirtiesContext
class InventoryReservationListenerIdempotencyTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9095");
        registry.add("app.kafka.topic", () -> "test-order-events-idem");
        registry.add("app.kafka.listen-topic", () -> "test-inv-events-idem");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
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
        verify(messagingTemplate, timeout(15000).atLeast(1))
                .convertAndSend(eq("/topic/messages"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
