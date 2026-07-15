package be.dnit.orderservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private String orderId;

    @Column(nullable = false)
    private String customerEmail;

    @ElementCollection
    @CollectionTable(name = "order_line_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLineItem> items = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    private Instant placedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Order() {}

    public Order(String orderId, String customerEmail, List<OrderLineItem> items,
                 BigDecimal totalAmount, OrderStatus status, Instant placedAt, Instant updatedAt) {
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.items = items != null ? items : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.status = status;
        this.placedAt = placedAt;
        this.updatedAt = updatedAt;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public List<OrderLineItem> getItems() { return items; }
    public void setItems(List<OrderLineItem> items) { this.items = items; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}