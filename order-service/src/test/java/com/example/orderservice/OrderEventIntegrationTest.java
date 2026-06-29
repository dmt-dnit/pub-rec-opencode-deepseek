package com.example.orderservice;

import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import com.example.sharedmodel.InventoryReservationEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = "test-integration-topic"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OrderEventIntegrationTest {

    static {
        System.setProperty("app.kafka.topic", "test-integration-topic");
        System.setProperty("app.kafka.listen-topic", "test-integration-topic");
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void shouldUpdateOrderStatusOnInventoryReservation() throws Exception {
        String orderId = UUID.randomUUID().toString();
        var now = Instant.now();
        Order order = new Order(orderId, "customer1@example.test",
                List.of(new OrderLineItem("SKU-001", 1)),
                new BigDecimal("9.99"), OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        kafkaTemplate.send("test-integration-topic", orderId,
                new InventoryReservationEvent(orderId,
                        InventoryReservationEvent.ReservationStatus.RESERVED,
                        null, Instant.now()));

        for (int i = 0; i < 20; i++) {
            Thread.sleep(500);
            Order updated = orderRepository.findById(orderId).orElseThrow();
            if (updated.getStatus() == OrderStatus.CONFIRMED) {
                return;
            }
        }
        Order updated = orderRepository.findById(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldPushOrderStatusUpdateViaWebSocket() throws Exception {
        String orderId = UUID.randomUUID().toString();
        var now = Instant.now();
        Order order = new Order(orderId, "ws-customer@example.test",
                List.of(new OrderLineItem("SKU-001", 3)),
                new BigDecimal("29.97"), OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        kafkaTemplate.send("test-integration-topic", orderId,
                new InventoryReservationEvent(orderId,
                        InventoryReservationEvent.ReservationStatus.RESERVED,
                        null, Instant.now()));

        ArgumentCaptor<OrderResponse> captor = ArgumentCaptor.forClass(OrderResponse.class);
        verify(messagingTemplate, timeout(15000))
                .convertAndSend(eq("/topic/messages"), captor.capture());

        OrderResponse response = captor.getValue();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.customerEmail()).isEqualTo("ws-customer@example.test");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sku()).isEqualTo("SKU-001");
        assertThat(response.items().get(0).quantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("29.97"));
    }

    @Test
    void shouldReturnOrdersWithItemsViaApi() throws Exception {
        String orderId = UUID.randomUUID().toString();
        var now = Instant.now();
        Order order = new Order(orderId, "api-customer@example.test",
                List.of(new OrderLineItem("SKU-002", 5)),
                new BigDecimal("49.95"), OrderStatus.PENDING, now, now);
        orderRepository.save(order);

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].customerEmail").value("api-customer@example.test"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].totalAmount").value(49.95))
                .andExpect(jsonPath("$[0].items").isArray())
                .andExpect(jsonPath("$[0].items[0].sku").value("SKU-002"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(5));
    }
}
