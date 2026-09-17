package com.commerce.catalog.product.domain.product_publication;

import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PublicationSnapshot(
        UUID publicationId,
        ProductId productId,
        long productVersion,
        PublicationCandidate candidate,
        List<ConfiguredVariant> variants) {
    public PublicationSnapshot {
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(candidate, "candidate");
        variants = List.copyOf(Objects.requireNonNull(variants, "variants"));
        if (productVersion < 1) {
            throw new IllegalArgumentException("productVersion must be positive");
        }
    }
}
