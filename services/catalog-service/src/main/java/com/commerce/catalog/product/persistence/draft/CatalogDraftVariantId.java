package com.commerce.catalog.product.persistence.draft;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogDraftVariantId implements Serializable {
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    protected CatalogDraftVariantId() {}

    public CatalogDraftVariantId(UUID productId, UUID variantId) {
        this.productId = Objects.requireNonNull(productId, "productId");
        this.variantId = Objects.requireNonNull(variantId, "variantId");
    }

    public UUID productId() {
        return productId;
    }

    public UUID variantId() {
        return variantId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogDraftVariantId that)) {
            return false;
        }
        return productId.equals(that.productId) && variantId.equals(that.variantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, variantId);
    }
}
