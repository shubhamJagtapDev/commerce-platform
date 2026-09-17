package com.commerce.catalog.product.persistence.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_public_listing")
public class CatalogPublicListingEntity {
    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "publication_id", nullable = false)
    private UUID publicationId;

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

    @Column(name = "representative_amount", nullable = false, columnDefinition = "catalog_inr_amount")
    private BigDecimal representativeAmount;

    @Column(name = "price_from", nullable = false)
    private boolean priceFrom;

    @Column(name = "sellable_count", nullable = false)
    private short sellableCount;

    protected CatalogPublicListingEntity() {}

    public CatalogPublicListingEntity(
            UUID productId,
            UUID publicationId,
            String name,
            String shortDescription,
            UUID mainCategoryId,
            @Nullable UUID subcategoryId,
            BigDecimal representativeAmount,
            boolean priceFrom,
            int sellableCount) {
        this.productId = Objects.requireNonNull(productId, "productId");
        this.publicationId = Objects.requireNonNull(publicationId, "publicationId");
        this.name = Objects.requireNonNull(name, "name");
        this.shortDescription = Objects.requireNonNull(shortDescription, "shortDescription");
        this.mainCategoryId = Objects.requireNonNull(mainCategoryId, "mainCategoryId");
        this.subcategoryId = subcategoryId;
        this.currency = "INR";
        this.representativeAmount = Objects.requireNonNull(representativeAmount, "representativeAmount");
        this.priceFrom = priceFrom;
        this.sellableCount = (short) sellableCount;
    }
}
