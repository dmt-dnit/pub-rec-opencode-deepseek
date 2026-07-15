package be.dnit.inventoryservice;

import be.dnit.inventoryservice.model.OutboxEvent;
import be.dnit.inventoryservice.publisher.InventoryEventPublisher;
import be.dnit.inventoryservice.repository.OutboxEventRepository;
import be.dnit.inventoryservice.service.OutboxRelay;
import be.dnit.sharedmodel.InventoryReservationEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"test-scheduled-order", "test-scheduled-inv"})
@ActiveProfiles("test")
@DirtiesContext
class OutboxRelayScheduledInvocationTest {

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.topic", () -> "test-scheduled-inv");
        registry.add("app.kafka.listen-topic", () -> "test-scheduled-order");
    }

    @MockitoBean
    private InventoryEventPublisher publisher;

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
    void scheduledRelayTransactsAndTransitionsOutboxRowToSent() throws Exception {
        doReturn(CompletableFuture.completedFuture(null))
                .when(publisher).publish(any(InventoryReservationEvent.class));

        InventoryReservationEvent event = new InventoryReservationEvent(
                "order-scheduled-test",
                InventoryReservationEvent.ReservationStatus.RESERVED,
                null,
                Instant.now());
        String payload = objectMapper.writeValueAsString(event);
        OutboxEvent row = new OutboxEvent("order-scheduled-test", payload, "test-scheduled-inv",
                OutboxEvent.Status.PENDING, Instant.now());
        outboxEventRepository.save(row);

        outboxRelay.scheduledRelay();

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getStatus())
                .as("scheduledRelay() must transit the outbox row to SENT via proxy")
                .isEqualTo(OutboxEvent.Status.SENT);
        assertThat(all.get(0).getSentAt()).isNotNull();
    }

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
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-scheduled");
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
