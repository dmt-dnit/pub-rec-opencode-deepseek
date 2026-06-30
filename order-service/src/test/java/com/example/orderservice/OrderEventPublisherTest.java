package com.example.orderservice;

import com.example.sharedmodel.OrderItem;
import com.example.sharedmodel.OrderPlacedEvent;
import com.example.orderservice.publisher.OrderEventPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @SuppressWarnings("unchecked")
    @Captor
    private ArgumentCaptor<ProducerRecord<String, OrderPlacedEvent>> recordCaptor;

    @BeforeEach
    void setUpMdc() {
        MDC.put("correlationId", "test-corr-id-001");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPublishOrderPlacedEventWithCorrelationIdHeader() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(new CompletableFuture<>());

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "test-topic");

        var now = Instant.parse("2026-06-15T12:00:00Z");
        var items = List.of(new OrderItem("SKU-001", 2));
        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-1", "customer1@example.test", items, new BigDecimal("19.98"), now
        );
        publisher.publish(event);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, OrderPlacedEvent> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("test-topic");
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(record.value().customerEmail()).isEqualTo("customer1@example.test");
        assertThat(record.value().totalAmount()).isEqualByComparingTo("19.98");

        Header corrHeader = record.headers().lastHeader("X-Correlation-Id");
        assertThat(corrHeader).isNotNull();
        assertThat(new String(corrHeader.value(), StandardCharsets.UTF_8)).isEqualTo("test-corr-id-001");
    }

    @Test
    void shouldPublishWithoutCorrelationIdHeaderWhenMdcIsEmpty() {
        MDC.clear(); // override the @BeforeEach setup

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(new CompletableFuture<>());

        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "test-topic");

        var now = Instant.parse("2026-06-15T12:00:00Z");
        OrderPlacedEvent event = new OrderPlacedEvent(
                "order-2", "customer2@example.test", List.of(new OrderItem("SKU-002", 1)),
                new BigDecimal("9.99"), now
        );
        publisher.publish(event);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, OrderPlacedEvent> record = recordCaptor.getValue();
        assertThat(record.key()).isEqualTo("order-2");
        assertThat(record.headers().lastHeader("X-Correlation-Id")).isNull();
    }
}
