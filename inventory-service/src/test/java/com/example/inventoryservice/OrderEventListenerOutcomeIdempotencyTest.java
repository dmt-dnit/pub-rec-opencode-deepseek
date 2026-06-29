package com.example.inventoryservice;

import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.publisher.InventoryEventPublisher;
import com.example.inventoryservice.repository.ProcessedOrderRepository;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Outcome-idempotency tests: a redelivered OrderPlacedEvent (same orderId) must
 * produce exactly one published InventoryReservationEvent and one WebSocket feed
 * item — regardless of whether the first outcome was RESERVED or REJECTED.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-order-events-outcome", "test-reservations-out"})
@ActiveProfiles("test")
@DirtiesContext
class OrderEventListenerOutcomeIdempotencyTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-inv-events-outcome");
        registry.add("app.kafka.listen-topic", () -> "test-order-events-outcome");
    }

    /** Replace real publisher with a mock — no actual Kafka send, but calls are trackable. */
    @MockitoBean
    private InventoryEventPublisher publisher;

    /** Replace real WebSocket template with a mock — calls are trackable. */
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    /**
     * Provide testReservationContainerFactory required by TestReservationConsumer.
     * That component is a @Component on the test classpath and is picked up by
     * @SpringBootTest — it needs this factory to register its @KafkaListener.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean("testReservationContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent> testReservationContainerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            JacksonJsonDeserializer<InventoryReservationEvent> deserializer =
                    new JacksonJsonDeserializer<>(InventoryReservationEvent.class);
            deserializer.setUseTypeHeaders(false);
            deserializer.addTrustedPackages("com.example.sharedmodel");

            ErrorHandlingDeserializer<InventoryReservationEvent> errorHandlingDeserializer =
                    new ErrorHandlingDeserializer<>(deserializer);

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-reservations-outcome");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            ConsumerFactory<String, InventoryReservationEvent> consumerFactory =
                    new DefaultKafkaConsumerFactory<>(props,
                            new StringDeserializer(),
                            errorHandlingDeserializer);

            ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }
    }

    @BeforeEach
    void cleanState() {
        processedOrderRepository.deleteAll();
        productRepository.deleteAll();
    }

    /**
     * Duplicate SUCCESS path:
     * - Two identical satisfiable events arrive for the same orderId.
     * - Expected: stock decremented exactly once, publisher called exactly once,
     *   WebSocket feed pushed exactly once.
     */
    @Test
    void duplicateSuccessPublishesExactlyOnce() throws Exception {
        productRepository.save(new Product("SKU-001", "Widget", 10));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-success-dup", "test@example.com",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-events-outcome", "order-success-dup", event);
        kafkaTemplate.send("test-order-events-outcome", "order-success-dup", event);

        // Wait long enough for both messages to be consumed and processed.
        Thread.sleep(6000);

        // Stock decremented exactly once (10 → 9).
        Product product = productRepository.findById("SKU-001").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(9);

        // Exactly one Kafka publish — second delivery was a no-op.
        verify(publisher, times(1)).publish(any(InventoryReservationEvent.class));

        // Exactly one WebSocket push — second delivery was a no-op.
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/messages"), any(InventoryReservationEvent.class));
    }

    /**
     * Duplicate REJECTION path:
     * - Two identical unsatisfiable events arrive for the same orderId (SKU-003 has 0 stock).
     * - Expected: exactly one REJECTED event published, ProcessedOrder marker saved,
     *   second delivery is a complete no-op (no second publish, no second WebSocket push,
     *   stock remains 0).
     */
    @Test
    void duplicateRejectionPublishesExactlyOnce() throws Exception {
        productRepository.save(new Product("SKU-003", "Gizmo", 0));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-reject-dup", "test@example.com",
                List.of(new OrderItem("SKU-003", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-events-outcome", "order-reject-dup", event);
        kafkaTemplate.send("test-order-events-outcome", "order-reject-dup", event);

        // Wait long enough for both messages to be consumed and processed.
        Thread.sleep(6000);

        // Exactly one REJECTED publish — second delivery was a no-op.
        verify(publisher, times(1)).publish(argThat(e ->
                e.status() == InventoryReservationEvent.ReservationStatus.REJECTED));

        // Exactly one WebSocket push — second delivery was a no-op.
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/messages"), any(InventoryReservationEvent.class));

        // ProcessedOrder marker was saved on the REJECTED path.
        assertThat(processedOrderRepository.existsById("order-reject-dup")).isTrue();

        // Stock is still 0 — no mutation happened.
        Product product = productRepository.findById("SKU-003").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(0);
    }
}
