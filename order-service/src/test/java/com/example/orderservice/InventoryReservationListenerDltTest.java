package com.example.orderservice;

import com.example.orderservice.repository.OrderRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-inv-dlt", "test-inv-dlt.DLT"})
@ActiveProfiles("test")
@DirtiesContext
class InventoryReservationListenerDltTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-order-dlt");
        registry.add("app.kafka.listen-topic", () -> "test-inv-dlt");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private DltCaptor dltCaptor;

    @MockitoBean
    private OrderRepository orderRepository;

    @TestConfiguration
    static class DltTestConfig {
        @Bean
        DltCaptor dltCaptor() {
            return new DltCaptor();
        }
    }

    static class DltCaptor {
        private final BlockingQueue<InventoryReservationEvent> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "test-inv-dlt.DLT", groupId = "dlt-captor-group")
        void onDltMessage(InventoryReservationEvent event) {
            received.add(event);
        }

        InventoryReservationEvent poll(long timeoutSeconds) throws InterruptedException {
            return received.poll(timeoutSeconds, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldRouteExceptionToDltAfterRetries() throws Exception {
        doThrow(new RuntimeException("Forced processing failure"))
                .when(orderRepository).findById(any());

        String orderId = UUID.randomUUID().toString();
        InventoryReservationEvent event = new InventoryReservationEvent(
                orderId, InventoryReservationEvent.ReservationStatus.RESERVED,
                null, Instant.now());

        kafkaTemplate.send("test-inv-dlt", orderId, event);

        InventoryReservationEvent dltRecord = dltCaptor.poll(25);
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.orderId()).isEqualTo(orderId);
    }
}
