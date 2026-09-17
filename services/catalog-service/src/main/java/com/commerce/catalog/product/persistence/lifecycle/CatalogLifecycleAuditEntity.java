package com.commerce.catalog.product.persistence.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_lifecycle_audit")
public class CatalogLifecycleAuditEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(name = "actor_key_id", nullable = false, length = 64)
    private String actorKeyId;

    @Column(name = "actor_hash", nullable = false)
    private byte[] actorHash;

    @Column(name = "operation_code", nullable = false, length = 32)
    private String operationCode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private @Nullable UUID variantId;

    @Column(name = "before_state", length = 16)
    private @Nullable String beforeState;

    @Column(name = "before_version")
    private @Nullable Long beforeVersion;

    @Column(name = "after_state", nullable = false, length = 16)
    private String afterState;

    @Column(name = "after_version", nullable = false)
    private long afterVersion;

    @Column(name = "publication_id")
    private @Nullable UUID publicationId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected CatalogLifecycleAuditEntity() {}

    CatalogLifecycleAuditEntity(
            UUID id,
            UUID commandId,
            String actorKeyId,
            byte[] actorHash,
            String operationCode,
            UUID productId,
            @Nullable UUID variantId,
            @Nullable String beforeState,
            @Nullable Long beforeVersion,
            String afterState,
            long afterVersion,
            @Nullable UUID publicationId,
            Instant recordedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.commandId = Objects.requireNonNull(commandId, "commandId");
        this.actorKeyId = Objects.requireNonNull(actorKeyId, "actorKeyId");
        this.actorHash = actorHash.clone();
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.variantId = variantId;
        this.beforeState = beforeState;
        this.beforeVersion = beforeVersion;
        this.afterState = Objects.requireNonNull(afterState, "afterState");
        this.afterVersion = afterVersion;
        this.publicationId = publicationId;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
