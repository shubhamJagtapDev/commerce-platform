package com.commerce.catalog.product.persistence.variant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_variant")
public class CatalogVariantEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "first_publication_id")
    private @Nullable UUID firstPublicationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CatalogVariantEntity() {}

    public CatalogVariantEntity(UUID id, UUID productId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    @Nullable
    public UUID firstPublicationId() {
        return firstPublicationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void markFirstPublication(UUID publicationId) {
        if (firstPublicationId == null) {
            firstPublicationId = Objects.requireNonNull(publicationId, "publicationId");
        }
    }
}
