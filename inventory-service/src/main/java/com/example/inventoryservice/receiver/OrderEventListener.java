package com.example.inventoryservice.receiver;

import com.example.inventoryservice.service.OutboxRelay;
import com.example.inventoryservice.service.ReservationService;
import com.example.sharedmodel.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ReservationService reservationService;
    private final OutboxRelay outboxRelay;

    public OrderEventListener(ReservationService reservationService, OutboxRelay outboxRelay) {
        this.reservationService = reservationService;
        this.outboxRelay = outboxRelay;
    }

    @KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent: orderId={}, customerEmail={}, items={}",
                event.orderId(), event.customerEmail(), event.items());
        reservationService.reserve(event);
        outboxRelay.processPending();
    }
}
