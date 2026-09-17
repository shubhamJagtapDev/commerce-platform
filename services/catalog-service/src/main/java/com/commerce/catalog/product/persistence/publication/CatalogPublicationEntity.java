package com.commerce.catalog.product.persistence.publication;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_publication")
public class CatalogPublicationEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_version", nullable = false)
    private long productVersion;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "short_description", nullable = false, length = 2000)
    private String shortDescription;

    @Column(name = "main_category_id", nullable = false)
    private UUID mainCategoryId;

    @Column(name = "subcategory_id")
    private @Nullable UUID subcategoryId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", nullable = false, columnDefinition = "jsonb")
    private List<String> imageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "long_description", columnDefinition = "jsonb")
    private @Nullable JsonNode longDescription;

    @Column(name = "option_1_name", length = 50)
    private @Nullable String option1Name;

    @Column(name = "option_2_name", length = 50)
    private @Nullable String option2Name;

    @Column(name = "sellable_count", nullable = false)
    private short sellableCount;

    @Column(name = "sealed", nullable = false)
    private boolean sealed;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected CatalogPublicationEntity() {}

    public CatalogPublicationEntity(
            UUID id,
            UUID productId,
            long productVersion,
            String name,
            String shortDescription,
            UUID mainCategoryId,
            @Nullable UUID subcategoryId,
            List<String> imageUrls,
            @Nullable JsonNode longDescription,
            List<String> optionDimensions,
            int sellableCount,
            Instant appliedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.productVersion = productVersion;
        this.name = Objects.requireNonNull(name, "name");
        this.shortDescription = Objects.requireNonNull(shortDescription, "shortDescription");
        this.mainCategoryId = Objects.requireNonNull(mainCategoryId, "mainCategoryId");
        this.subcategoryId = subcategoryId;
        this.currency = "INR";
        this.imageUrls = List.copyOf(Objects.requireNonNull(imageUrls, "imageUrls"));
        this.longDescription = longDescription;
        this.option1Name = optionDimensions.isEmpty() ? null : optionDimensions.getFirst();
        this.option2Name = optionDimensions.size() < 2 ? null : optionDimensions.get(1);
        this.sellableCount = (short) sellableCount;
        this.sealed = false;
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public long productVersion() {
        return productVersion;
    }

    public String name() {
        return name;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public UUID mainCategoryId() {
        return mainCategoryId;
    }

    @Nullable
    public UUID subcategoryId() {
        return subcategoryId;
    }

    public List<String> imageUrls() {
        return List.copyOf(imageUrls);
    }

    @Nullable
    public JsonNode longDescription() {
        return longDescription;
    }

    public List<String> optionDimensions() {
        if (option1Name == null) {
            return List.of();
        }
        if (option2Name == null) {
            return List.of(option1Name);
        }
        return List.of(option1Name, option2Name);
    }

    public int sellableCount() {
        return sellableCount;
    }

    public boolean sealed() {
        return sealed;
    }

    public void seal() {
        sealed = true;
    }
}
