package com.example.inventoryservice.publisher;

import com.example.sharedmodel.InventoryReservationEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Service
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final KafkaTemplate<String, InventoryReservationEvent> kafkaTemplate;
    private final String topic;

    public InventoryEventPublisher(KafkaTemplate<String, InventoryReservationEvent> kafkaTemplate,
                                   @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public CompletableFuture<SendResult<String, InventoryReservationEvent>> publish(InventoryReservationEvent event) {
        ProducerRecord<String, InventoryReservationEvent> record =
                new ProducerRecord<>(topic, event.orderId(), event);

        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            record.headers().add(CORRELATION_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }

        CompletableFuture<SendResult<String, InventoryReservationEvent>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published: topic={}, partition={}, offset={}, correlationId={}, event={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        correlationId,
                        event);
            } else {
                log.error("Failed to publish event: {}", event, ex);
            }
        });

        return future;
    }
}
