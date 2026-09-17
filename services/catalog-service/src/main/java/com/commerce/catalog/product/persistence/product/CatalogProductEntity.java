package com.commerce.catalog.product.persistence.product;

import com.commerce.catalog.product.domain.product_models.ProductState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_product")
public class CatalogProductEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "state", nullable = false, columnDefinition = "catalog_product_state")
    private ProductState state;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "last_command_id", nullable = false)
    private UUID lastCommandId;

    @Column(name = "current_publication_id")
    private @Nullable UUID currentPublicationId;

    @Column(name = "first_published_at")
    private @Nullable Instant firstPublishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatalogProductEntity() {}

    public CatalogProductEntity(UUID id, UUID lastCommandId, Instant now) {
        this.id = Objects.requireNonNull(id, "id");
        this.state = ProductState.DRAFT;
        this.version = 0;
        this.lastCommandId = Objects.requireNonNull(lastCommandId, "lastCommandId");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public ProductState state() {
        return state;
    }

    public long version() {
        return version;
    }

    public UUID lastCommandId() {
        return lastCommandId;
    }

    @Nullable
    public UUID currentPublicationId() {
        return currentPublicationId;
    }

    @Nullable
    public Instant firstPublishedAt() {
        return firstPublishedAt;
    }

    public void markPublished(UUID publicationId, UUID commandId, Instant now) {
        state = ProductState.PUBLISHED;
        currentPublicationId = Objects.requireNonNull(publicationId, "publicationId");
        if (firstPublishedAt == null) {
            firstPublishedAt = Objects.requireNonNull(now, "now");
        }
        markCommand(commandId, now);
    }

    public void markWithdrawn(UUID commandId, Instant now) {
        state = ProductState.WITHDRAWN;
        markCommand(commandId, now);
    }

    public void markCandidateReplaced(UUID commandId, Instant now) {
        markCommand(commandId, now);
    }

    private void markCommand(UUID commandId, Instant now) {
        lastCommandId = Objects.requireNonNull(commandId, "commandId");
        updatedAt = Objects.requireNonNull(now, "now");
    }
}
