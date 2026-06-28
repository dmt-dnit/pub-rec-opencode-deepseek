package com.example.inventoryservice;

import com.example.inventoryservice.service.ReservationService;
import com.example.sharedmodel.InventoryReservationEvent;
import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-order-dlt", "test-order-dlt.DLT"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9094", "port=9094"})
@ActiveProfiles("test")
@DirtiesContext
class OrderEventListenerDltTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9094");
        registry.add("app.kafka.topic", () -> "test-inv-dlt");
        registry.add("app.kafka.listen-topic", () -> "test-order-dlt");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private DltCaptor dltCaptor;

    @MockBean
    private ReservationService reservationService;

    @TestConfiguration
    static class DltTestConfig {
        @Bean
        DltCaptor dltCaptor() {
            return new DltCaptor();
        }

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

    static class DltCaptor {
        private final BlockingQueue<OrderPlacedEvent> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "test-order-dlt.DLT", groupId = "dlt-captor-group")
        void onDltMessage(OrderPlacedEvent event) {
            received.add(event);
        }

        OrderPlacedEvent poll(long timeoutSeconds) throws InterruptedException {
            return received.poll(timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRouteExceptionToDltAfterRetries() throws Exception {
        doThrow(new RuntimeException("Forced processing failure"))
                .when(reservationService).reserve(any());

        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-1", "customer@example.test",
                List.of(new OrderItem("SKU-001", 1)),
                new BigDecimal("9.99"), Instant.now());

        kafkaTemplate.send("test-order-dlt", "order-1", event);

        OrderPlacedEvent dltRecord = dltCaptor.poll(25);
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.orderId()).isEqualTo("order-1");
    }
}
