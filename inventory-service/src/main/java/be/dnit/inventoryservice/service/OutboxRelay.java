package be.dnit.inventoryservice.service;

import be.dnit.inventoryservice.model.OutboxEvent;
import be.dnit.inventoryservice.publisher.InventoryEventPublisher;
import be.dnit.inventoryservice.repository.OutboxEventRepository;
import be.dnit.sharedmodel.InventoryReservationEvent;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final InventoryEventPublisher publisher;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OutboxRelay> self;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       InventoryEventPublisher publisher,
                       SimpMessagingTemplate messagingTemplate,
                       ObjectMapper objectMapper,
                       ObjectProvider<OutboxRelay> self) {
        this.outboxEventRepository = outboxEventRepository;
        this.publisher = publisher;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:500}")
    public void scheduledRelay() {
        self.getObject().processPending();
    }

    @Transactional
    public void processPending() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Found {} pending outbox events to relay", pending.size());

        for (OutboxEvent row : pending) {
            try {
                InventoryReservationEvent event = objectMapper.readValue(row.getPayload(), InventoryReservationEvent.class);
                CompletableFuture<?> future = publisher.publish(event);
                if (future != null) {
                    future.get(10, TimeUnit.SECONDS);
                }

                row.setStatus(OutboxEvent.Status.SENT);
                row.setSentAt(Instant.now());
                outboxEventRepository.save(row);

                messagingTemplate.convertAndSend("/topic/messages", event);

                log.info("Relayed outbox event id={} orderId={} status={}", row.getId(), row.getOrderId(), event.status());
            } catch (Exception e) {
                log.error("Failed to relay outbox event id={} orderId={}: {}", row.getId(), row.getOrderId(), e.getMessage());
            }
        }
    }
}
