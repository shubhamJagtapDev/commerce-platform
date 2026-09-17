package com.commerce.catalog.product.persistence.publication;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogPublicationVariantId implements Serializable {
    @Column(name = "publication_id", nullable = false)
    private UUID publicationId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    protected CatalogPublicationVariantId() {}

    public CatalogPublicationVariantId(UUID publicationId, UUID variantId) {
        this.publicationId = Objects.requireNonNull(publicationId, "publicationId");
        this.variantId = Objects.requireNonNull(variantId, "variantId");
    }

    public UUID publicationId() {
        return publicationId;
    }

    public UUID variantId() {
        return variantId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogPublicationVariantId that)) {
            return false;
        }
        return publicationId.equals(that.publicationId) && variantId.equals(that.variantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicationId, variantId);
    }
}
