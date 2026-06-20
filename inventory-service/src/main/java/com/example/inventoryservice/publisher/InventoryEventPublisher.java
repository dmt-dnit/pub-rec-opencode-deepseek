package com.example.inventoryservice.publisher;

import com.example.sharedmodel.InventoryReservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final KafkaTemplate<String, InventoryReservationEvent> kafkaTemplate;
    private final String topic;

    public InventoryEventPublisher(KafkaTemplate<String, InventoryReservationEvent> kafkaTemplate,
                                   @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(InventoryReservationEvent event) {
        CompletableFuture<SendResult<String, InventoryReservationEvent>> future =
                kafkaTemplate.send(topic, event.orderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Published: topic={}, partition={}, offset={}, event={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        event);
            } else {
                log.error("Failed to publish event: {}", event, ex);
            }
        });
    }
}
