package com.example.inventoryservice;

import com.example.inventoryservice.model.OutboxEvent;
import com.example.inventoryservice.publisher.InventoryEventPublisher;
import com.example.inventoryservice.repository.OutboxEventRepository;
import com.example.inventoryservice.service.OutboxRelay;
import com.example.sharedmodel.InventoryReservationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Concurrency test for {@link OutboxRelay#processPending()}. Verifies that
 * under concurrent invocations (scheduled poller + listener nudge, or any
 * two racing callers) each outbox row is published and STOMP-broadcast
 * exactly once — no duplicate Kafka publishes, no duplicate WebSocket pushes.
 *
 * Uses a pessimistic write lock on the PENDING fetch to serialize concurrent
 * relay transactions.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-concurrency-order", "test-concurrency-inv"})
@ActiveProfiles("test")
@DirtiesContext
class OutboxRelayConcurrencyTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-concurrency-inv");
        registry.add("app.kafka.listen-topic", () -> "test-concurrency-order");
    }

    @MockitoBean
    private InventoryEventPublisher publisher;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanState() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void concurrentProcessPendingProcessesEachRowExactlyOnce() throws Exception {
        String topic = "test-concurrency-inv";

        for (int i = 0; i < 3; i++) {
            InventoryReservationEvent event = new InventoryReservationEvent(
                    "order-" + i,
                    InventoryReservationEvent.ReservationStatus.RESERVED,
                    null,
                    Instant.now());
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent row = new OutboxEvent("order-" + i, payload, topic,
                    OutboxEvent.Status.PENDING, Instant.now());
            outboxEventRepository.save(row);
        }

        when(publisher.publish(any(InventoryReservationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> f1 = executor.submit(() -> outboxRelay.processPending());
        Future<?> f2 = executor.submit(() -> outboxRelay.processPending());

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        verify(publisher, times(3)).publish(any(InventoryReservationEvent.class));
        verify(messagingTemplate, times(3))
                .convertAndSend(eq("/topic/messages"), any(InventoryReservationEvent.class));

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).allMatch(r -> r.getStatus() == OutboxEvent.Status.SENT);
    }

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
