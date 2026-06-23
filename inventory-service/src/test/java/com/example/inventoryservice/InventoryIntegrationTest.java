package com.example.inventoryservice;

import com.example.inventoryservice.publisher.InventoryEventPublisher;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InventoryIntegrationTest {

    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    private static boolean dockerAvailable;

    static {
        System.setProperty("app.kafka.listen-topic", "test-orders-in");
        System.setProperty("app.kafka.topic", "test-reservations-out");
    }

    @BeforeAll
    static void setUp() {
        dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        if (dockerAvailable) {
            kafka.start();
        }
    }

    @AfterAll
    static void tearDown() {
        if (dockerAvailable && kafka.isRunning()) {
            kafka.stop();
        }
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        if (dockerAvailable) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        }
    }

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Autowired
    private InventoryEventPublisher publisherService;

    @Autowired
    private TestReservationConsumer testConsumer;

    @Test
    void shouldPublishAndReceiveInventoryReservationEvent() throws Exception {
        Assumptions.assumeTrue(dockerAvailable,
                "No container runtime available — skipping Testcontainers-based test");

        OrderPlacedEvent orderEvent = new OrderPlacedEvent(
                "order-1",
                "customer@example.test",
                List.of(new OrderItem("SKU-001", 2)),
                BigDecimal.valueOf(29.98),
                Instant.parse("2026-06-16T12:00:00Z")
        );

        kafkaTemplate.send("test-orders-in", orderEvent.orderId(), orderEvent);

        InventoryReservationEvent received = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(received).isNotNull();
        assertThat(received.orderId()).isEqualTo("order-1");
        assertThat(received.status()).isEqualTo(InventoryReservationEvent.ReservationStatus.RESERVED);
    }

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean("testReservationContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent> testReservationContainerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

            JsonDeserializer<InventoryReservationEvent> deserializer =
                    new JsonDeserializer<>(InventoryReservationEvent.class);
            deserializer.setUseTypeHeaders(false);
            deserializer.addTrustedPackages("com.example.sharedmodel");

            ErrorHandlingDeserializer<InventoryReservationEvent> errorHandlingDeserializer =
                    new ErrorHandlingDeserializer<>(deserializer);

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-reservations");
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
}
