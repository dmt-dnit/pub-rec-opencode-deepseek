package com.example.inventoryservice;

import com.example.sharedmodel.InventoryReservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
class TestReservationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestReservationConsumer.class);

    private final BlockingQueue<InventoryReservationEvent> received = new LinkedBlockingQueue<>();

    @KafkaListener(topics = "test-reservations-out", groupId = "test-group-reservations",
                   containerFactory = "testReservationContainerFactory")
    void onEvent(InventoryReservationEvent event) {
        log.info("[TEST] Received InventoryReservationEvent: orderId={}, status={}", event.orderId(), event.status());
        received.add(event);
    }

    InventoryReservationEvent poll(Duration timeout) throws InterruptedException {
        return received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
