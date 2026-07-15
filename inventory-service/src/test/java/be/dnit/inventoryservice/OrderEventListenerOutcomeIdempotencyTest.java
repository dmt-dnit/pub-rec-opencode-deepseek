package be.dnit.inventoryservice;

import be.dnit.inventoryservice.model.OutboxEvent;
import be.dnit.inventoryservice.model.Product;
import be.dnit.inventoryservice.publisher.InventoryEventPublisher;
import be.dnit.inventoryservice.repository.OutboxEventRepository;
import be.dnit.inventoryservice.repository.ProcessedOrderRepository;
import be.dnit.inventoryservice.repository.ProductRepository;
import be.dnit.sharedmodel.InventoryReservationEvent;
import be.dnit.sharedmodel.OrderItem;
import be.dnit.sharedmodel.OrderPlacedEvent;
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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outcome-idempotency tests: a redelivered OrderPlacedEvent (same orderId) must
 * produce exactly one published InventoryReservationEvent and one WebSocket feed
 * item — regardless of whether the first outcome was RESERVED or REJECTED.
 * <p>
 * With the transactional outbox, publishing is delegated to {@code OutboxRelay}.
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

    @MockitoBean
    private InventoryEventPublisher publisher;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean("testReservationContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent> testReservationContainerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            JacksonJsonDeserializer<InventoryReservationEvent> deserializer =
                    new JacksonJsonDeserializer<>(InventoryReservationEvent.class);
            deserializer.setUseTypeHeaders(false);
            deserializer.addTrustedPackages("be.dnit.sharedmodel");

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
        outboxEventRepository.deleteAll();
        when(publisher.publish(any(InventoryReservationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void duplicateSuccessPublishesExactlyOnce() throws Exception {
        productRepository.save(new Product("SKU-001", "Widget", 10));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-success-dup", "test@example.com",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-events-outcome", "order-success-dup", event);
        kafkaTemplate.send("test-order-events-outcome", "order-success-dup", event);

        Thread.sleep(6000);

        Product product = productRepository.findById("SKU-001").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(9);

        verify(publisher, times(1)).publish(any(InventoryReservationEvent.class));

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/messages"), any(InventoryReservationEvent.class));

        List<OutboxEvent> outboxRows = outboxEventRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getOrderId()).isEqualTo("order-success-dup");
        assertThat(outboxRows.get(0).getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(outboxRows.get(0).getSentAt()).isNotNull();
    }

    @Test
    void duplicateRejectionPublishesExactlyOnce() throws Exception {
        productRepository.save(new Product("SKU-003", "Gizmo", 0));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-reject-dup", "test@example.com",
                List.of(new OrderItem("SKU-003", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-events-outcome", "order-reject-dup", event);
        kafkaTemplate.send("test-order-events-outcome", "order-reject-dup", event);

        Thread.sleep(6000);

        verify(publisher, times(1)).publish(argThat(e ->
                e.status() == InventoryReservationEvent.ReservationStatus.REJECTED));

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/messages"), any(InventoryReservationEvent.class));

        assertThat(processedOrderRepository.existsById("order-reject-dup")).isTrue();

        Product product = productRepository.findById("SKU-003").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(0);

        List<OutboxEvent> outboxRows = outboxEventRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getOrderId()).isEqualTo("order-reject-dup");
        assertThat(outboxRows.get(0).getStatus()).isEqualTo(OutboxEvent.Status.SENT);
        assertThat(outboxRows.get(0).getSentAt()).isNotNull();
    }
}
