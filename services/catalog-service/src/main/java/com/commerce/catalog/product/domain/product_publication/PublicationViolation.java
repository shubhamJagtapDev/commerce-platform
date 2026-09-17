package com.commerce.catalog.product.domain.product_publication;

import java.util.Objects;

public record PublicationViolation(String field, PublicationViolationCode code) {
    public PublicationViolation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
    }
}
