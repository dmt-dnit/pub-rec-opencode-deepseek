package com.example.orderservice;

import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import com.example.orderservice.publisher.OrderEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Captor
    private ArgumentCaptor<OrderPlacedEvent> eventCaptor;

    @Test
    void shouldPublishOrderPlacedEvent() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(new CompletableFuture<>());

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "test-topic");

        var now = Instant.parse("2026-06-15T12:00:00Z");
        var items = List.of(new OrderItem("SKU-001", 2));
        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-1", "customer1@example.test", items, new BigDecimal("19.98"), now
        );
        publisher.publish(event);

        verify(kafkaTemplate).send(eq("test-topic"), eq("order-1"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().customerEmail()).isEqualTo("customer1@example.test");
        assertThat(eventCaptor.getValue().totalAmount()).isEqualByComparingTo("19.98");
    }
}