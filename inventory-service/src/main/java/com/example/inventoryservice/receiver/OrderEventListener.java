package com.example.inventoryservice.receiver;

import com.example.inventoryservice.publisher.InventoryEventPublisher;
import com.example.inventoryservice.service.ReservationService;
import com.example.sharedmodel.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ReservationService reservationService;
    private final InventoryEventPublisher publisher;
    private final SimpMessagingTemplate messagingTemplate;

    public OrderEventListener(ReservationService reservationService,
                              InventoryEventPublisher publisher,
                              SimpMessagingTemplate messagingTemplate) {
        this.reservationService = reservationService;
        this.publisher = publisher;
        this.messagingTemplate = messagingTemplate;
    }

    @KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(OrderPlacedEvent event) {
        log.info("Received OrderPlacedEvent: orderId={}, customerEmail={}, items={}",
                event.orderId(), event.customerEmail(), event.items());

        reservationService.reserve(event).ifPresent(outcome -> {
            publisher.publish(outcome);
            messagingTemplate.convertAndSend("/topic/messages", outcome);
        });
    }
}
