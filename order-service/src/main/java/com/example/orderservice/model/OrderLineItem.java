package com.example.orderservice.model;

import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class OrderLineItem {

    private String sku;
    private int quantity;

    public OrderLineItem() {}

    public OrderLineItem(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderLineItem that)) return false;
        return quantity == that.quantity && Objects.equals(sku, that.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, quantity);
    }
}