package be.dnit.inventoryservice.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ProcessedOrder {

    @Id
    private String orderId;

    public ProcessedOrder() {}

    public ProcessedOrder(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
