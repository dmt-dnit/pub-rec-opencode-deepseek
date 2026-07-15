package be.dnit.orderservice.receiver;

import be.dnit.orderservice.dto.OrderResponse;
import be.dnit.orderservice.model.Order;
import be.dnit.orderservice.model.OrderStatus;
import be.dnit.orderservice.repository.OrderRepository;
import be.dnit.sharedmodel.InventoryReservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
            if (order.getStatus() != OrderStatus.PENDING) {
                log.info("Order {} already {}, skipping re-apply", order.getOrderId(), order.getStatus());
                return;
            }
            if (event.status() == InventoryReservationEvent.ReservationStatus.RESERVED) {
                order.setStatus(OrderStatus.CONFIRMED);
            } else {
                order.setStatus(OrderStatus.REJECTED);
            }
            order.setUpdatedAt(Instant.now());
            Order saved = orderRepository.save(order);
            // correlationId was set in MDC by CorrelationMdcRecordInterceptor before this listener ran
            OrderResponse response = OrderResponse.from(saved, MDC.get("correlationId"));
            messagingTemplate.convertAndSend("/topic/messages", response);
            log.info("Order {} updated to status {}", saved.getOrderId(), saved.getStatus());
        }, () -> log.warn("No order found for orderId: {}", event.orderId()));
    }
}