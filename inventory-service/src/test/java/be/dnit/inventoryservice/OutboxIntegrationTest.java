package be.dnit.inventoryservice;

import be.dnit.inventoryservice.model.OutboxEvent;
import be.dnit.inventoryservice.model.Product;
import be.dnit.inventoryservice.publisher.InventoryEventPublisher;
import be.dnit.inventoryservice.repository.OutboxEventRepository;
import be.dnit.inventoryservice.repository.ProcessedOrderRepository;
import be.dnit.inventoryservice.repository.ProductRepository;
import be.dnit.inventoryservice.service.OutboxRelay;
import be.dnit.sharedmodel.InventoryReservationEvent;
import be.dnit.sharedmodel.OrderItem;
import be.dnit.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Transactional outbox integration tests. Verifies crash-safe exactly-once
 * publish guarantees using EmbeddedKafka.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-outbox-order", "test-outbox-inv"})
@ActiveProfiles("test")
@DirtiesContext
class OutboxIntegrationTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-outbox-inv");
        registry.add("app.kafka.listen-topic", () -> "test-outbox-order");
    }

    @MockitoBean
    private InventoryEventPublisher publisher;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcessedOrderRepository processedOrderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

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
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-outs");
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
    }

    /**
     * A duplicate OrderPlacedEvent must produce exactly ONE outbox row and ONE
     * published InventoryReservationEvent (exactly-once). The second delivery
     * is idempotent — no second outbox row, no second publish.
     */
    @Test
    void duplicateOrderPlacedEventProducesOneOutboxRowAndOnePublishedEvent() throws Exception {
        productRepository.save(new Product("SKU-001", "Widget", 10));

        doReturn(CompletableFuture.completedFuture(null))
                .when(publisher).publish(any(InventoryReservationEvent.class));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-outbox-dup", "test@example.com",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-outbox-order", "order-outbox-dup", event);
        kafkaTemplate.send("test-outbox-order", "order-outbox-dup", event);

        Thread.sleep(6000);

        List<OutboxEvent> outboxRows = outboxEventRepository.findAll();
        assertThat(outboxRows).hasSize(1)
                .as("Duplicate delivery must not create a second outbox row");
        assertThat(outboxRows.get(0).getOrderId()).isEqualTo("order-outbox-dup");
        assertThat(outboxRows.get(0).getStatus()).isEqualTo(OutboxEvent.Status.SENT);

        verify(publisher, times(1)).publish(any(InventoryReservationEvent.class));

        Product product = productRepository.findById("SKU-001").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(9);
    }

    /**
     * A simulated publish failure on the first relay attempt does NOT lose the
     * event. The outbox row stays PENDING and is re-published on the next
     * attempt — the event is eventually delivered.
     */
    @Test
    void publishFailureDoesNotLoseEvent() throws Exception {
        productRepository.save(new Product("SKU-001", "Widget", 10));

        doThrow(new RuntimeException("Simulated broker failure"))
                .doReturn(CompletableFuture.completedFuture(null))
                .when(publisher).publish(any(InventoryReservationEvent.class));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-outbox-retry", "test@example.com",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-outbox-order", "order-outbox-retry", event);

        Thread.sleep(6000);

        List<OutboxEvent> rows = outboxEventRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus())
                .as("Outbox row must stay PENDING after publish failure")
                .isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(rows.get(0).getSentAt()).isNull();

        verify(publisher, times(1)).publish(any(InventoryReservationEvent.class));

        doReturn(CompletableFuture.completedFuture(null))
                .when(publisher).publish(any(InventoryReservationEvent.class));

        outboxRelay.processPending();

        rows = outboxEventRepository.findAll();
        assertThat(rows.get(0).getStatus())
                .as("Outbox row must be SENT after successful retry")
                .isEqualTo(OutboxEvent.Status.SENT);
        assertThat(rows.get(0).getSentAt()).isNotNull();

        verify(publisher, times(2)).publish(any(InventoryReservationEvent.class));

        Product product = productRepository.findById("SKU-001").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(9);
    }
}
