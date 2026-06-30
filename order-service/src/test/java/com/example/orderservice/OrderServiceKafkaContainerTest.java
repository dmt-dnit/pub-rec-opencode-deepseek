package com.example.orderservice;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.publisher.OrderEventPublisher;
import com.example.orderservice.repository.OrderRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for order-service — exercises the real Kafka
 * producer + consumer path with an actual broker container.
 *
 * Skipped automatically via {@code @Testcontainers(disabledWithoutDocker = true)}
 * when no Docker daemon is available; never hard-fails {@code ./mvnw verify}
 * on a Docker-less machine.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class OrderServiceKafkaContainerTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.kafka.topic", () -> "test-order-events-out");
        registry.add("app.kafka.listen-topic", () -> "test-inv-events-in");
    }

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderPlacedCaptor orderPlacedCaptor;

    // ------------------------------------------------------------------
    // Test 1: publish OrderPlacedEvent via real producer and verify it
    //         lands on the output topic by consuming it.
    // ------------------------------------------------------------------

    @Test
    void shouldPublishOrderPlacedEventToRealKafka() throws Exception {
        orderPlacedCaptor.clear();

        String orderId = UUID.randomUUID().toString();
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId,
                "tc-customer@example.test",
                List.of(new OrderItem("SKU-TC-1", 2)),
                new BigDecimal("19.98"),
                Instant.now()
        );

        orderEventPublisher.publish(event);

        OrderPlacedEvent received = orderPlacedCaptor.poll(Duration.ofSeconds(15));
        assertThat(received).isNotNull();
        assertThat(received.orderId()).isEqualTo(orderId);
        assertThat(received.customerEmail()).isEqualTo("tc-customer@example.test");
        assertThat(received.items()).hasSize(1);
        assertThat(received.items().get(0).sku()).isEqualTo("SKU-TC-1");
        assertThat(received.totalAmount()).isEqualByComparingTo(new BigDecimal("19.98"));
    }

    // ------------------------------------------------------------------
    // Test 2: consume real InventoryReservationEvent → order status
    //         transitions from PENDING to CONFIRMED.
    // ------------------------------------------------------------------

    @Test
    void shouldTransitionOrderStatusOnRealInventoryReservation() throws Exception {
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Order order = new Order(orderId, "tc-saga@example.test",
                List.of(new OrderLineItem("SKU-TC-2", 1)),
                new BigDecimal("9.99"), OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        kafkaTemplate.send("test-inv-events-in", orderId,
                new InventoryReservationEvent(orderId,
                        InventoryReservationEvent.ReservationStatus.RESERVED,
                        null, Instant.now()));

        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            Order updated = orderRepository.findById(orderId).orElseThrow();
            if (updated.getStatus() == OrderStatus.CONFIRMED) {
                return;
            }
        }
        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // ------------------------------------------------------------------
    // Test infrastructure — inner classes scoped to this test only,
    // so they do NOT pollute other @SpringBootTest contexts.
    // ------------------------------------------------------------------

    /** Captures OrderPlacedEvent messages from the publisher's output topic. */
    static class OrderPlacedCaptor {

        private static final Logger log = LoggerFactory.getLogger(OrderPlacedCaptor.class);
        private final BlockingQueue<OrderPlacedEvent> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "test-order-events-out",
                       groupId = "test-group-order-placed",
                       containerFactory = "testOrderPlacedContainerFactory")
        void onEvent(OrderPlacedEvent event) {
            log.info("[TEST] Captured OrderPlacedEvent: orderId={}", event.orderId());
            received.add(event);
        }

        OrderPlacedEvent poll(Duration timeout) throws InterruptedException {
            return received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void clear() {
            received.clear();
        }
    }

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean
        OrderPlacedCaptor orderPlacedCaptor() {
            return new OrderPlacedCaptor();
        }

        @Bean("testOrderPlacedContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> testOrderPlacedContainerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

            JacksonJsonDeserializer<OrderPlacedEvent> deserializer =
                    new JacksonJsonDeserializer<>(OrderPlacedEvent.class);
            deserializer.setUseTypeHeaders(false);
            deserializer.addTrustedPackages("com.example.sharedmodel");

            ErrorHandlingDeserializer<OrderPlacedEvent> errorHandlingDeserializer =
                    new ErrorHandlingDeserializer<>(deserializer);

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-order-placed");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

            ConsumerFactory<String, OrderPlacedEvent> consumerFactory =
                    new DefaultKafkaConsumerFactory<>(props,
                            new StringDeserializer(),
                            errorHandlingDeserializer);

            ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }
    }
}
