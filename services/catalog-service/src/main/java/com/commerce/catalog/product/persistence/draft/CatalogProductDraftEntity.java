package com.commerce.catalog.product.persistence.draft;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_product_draft")
public class CatalogProductDraftEntity {
    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "name", length = 200)
    private @Nullable String name;

    @Column(name = "short_description", length = 2000)
    private @Nullable String shortDescription;

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

    protected CatalogProductDraftEntity() {}

    public CatalogProductDraftEntity(
            UUID productId,
            @Nullable String name,
            @Nullable String shortDescription,
            UUID mainCategoryId,
            @Nullable UUID subcategoryId,
            List<String> imageUrls,
            @Nullable JsonNode longDescription,
            @Nullable String option1Name,
            @Nullable String option2Name) {
        this.productId = Objects.requireNonNull(productId, "productId");
        this.name = name;
        this.shortDescription = shortDescription;
        this.mainCategoryId = Objects.requireNonNull(mainCategoryId, "mainCategoryId");
        this.subcategoryId = subcategoryId;
        this.currency = "INR";
        this.imageUrls = List.copyOf(Objects.requireNonNull(imageUrls, "imageUrls"));
        this.longDescription = longDescription;
        this.option1Name = option1Name;
        this.option2Name = option2Name;
    }

    public UUID productId() {
        return productId;
    }

    @Nullable
    public String name() {
        return name;
    }

    @Nullable
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
}
