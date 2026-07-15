package be.dnit.orderservice.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class PriceCatalog {

    private static final Map<String, BigDecimal> PRICES = Map.of(
            "SKU-001", new BigDecimal("9.99"),
            "SKU-002", new BigDecimal("24.50"),
            "SKU-003", new BigDecimal("4.25")
    );

    public Optional<BigDecimal> getPrice(String sku) {
        return Optional.ofNullable(PRICES.get(sku));
    }

    public boolean exists(String sku) {
        return PRICES.containsKey(sku);
    }
}