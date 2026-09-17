package com.commerce.catalog.product.config;

import jakarta.validation.constraints.NotBlank;

public record HmacKeyProperties(
        @NotBlank String id, @NotBlank String material) {
    @Override
    public String toString() {
        return "HmacKeyProperties[id=" + id + ", material=REDACTED]";
    }
}
