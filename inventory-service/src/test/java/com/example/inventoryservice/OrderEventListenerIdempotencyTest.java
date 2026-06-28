package com.example.inventoryservice;

import com.example.inventoryservice.model.Product;
import com.example.inventoryservice.repository.ProductRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-order-events-idem"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"})
@ActiveProfiles("test")
@DirtiesContext
class OrderEventListenerIdempotencyTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9094");
        registry.add("app.kafka.topic", () -> "test-inv-events-idem");
        registry.add("app.kafka.listen-topic", () -> "test-order-events-idem");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ProductRepository productRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean("testReservationContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, InventoryReservationEvent> testReservationContainerFactory() {
            JsonDeserializer<InventoryReservationEvent> deserializer =
                    new JsonDeserializer<>(InventoryReservationEvent.class);
            deserializer.setUseTypeHeaders(false);
            deserializer.addTrustedPackages("com.example.sharedmodel");

            ErrorHandlingDeserializer<InventoryReservationEvent> errorHandlingDeserializer =
                    new ErrorHandlingDeserializer<>(deserializer);

            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9094");
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

    @Test
    void shouldDecrementStockOnlyOnceOnDuplicateEvent() throws Exception {
        productRepository.save(new Product("SKU-001", "Widget", 10));

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-1", "customer@example.test",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-events-idem", "order-1", event);

        Thread.sleep(5000);

        kafkaTemplate.send("test-order-events-idem", "order-1", event);

        Thread.sleep(3000);

        Product product = productRepository.findById("SKU-001").orElseThrow();
        assertThat(product.getQuantityOnHand()).isEqualTo(9);
    }
}
