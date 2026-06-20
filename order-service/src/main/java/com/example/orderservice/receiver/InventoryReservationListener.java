package com.example.orderservice.receiver;

import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class InventoryReservationListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationListener.class);

    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public InventoryReservationListener(OrderRepository orderRepository,
                                        SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    @KafkaListener(topics = "${app.kafka.listen-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReservation(InventoryReservationEvent event) {
        log.info("Received reservation: orderId={}, status={}", event.orderId(), event.status());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (event.status() == InventoryReservationEvent.ReservationStatus.RESERVED) {
                order.setStatus(OrderStatus.CONFIRMED);
            } else {
                order.setStatus(OrderStatus.REJECTED);
            }
            order.setUpdatedAt(Instant.now());
            Order saved = orderRepository.save(order);
            OrderResponse response = OrderResponse.from(saved);
            messagingTemplate.convertAndSend("/topic/messages", response);
            log.info("Order {} updated to status {}", saved.getOrderId(), saved.getStatus());
        }, () -> log.warn("No order found for orderId: {}", event.orderId()));
    }
}