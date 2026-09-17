package com.commerce.catalog.product.persistence.publication;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_publication_variant")
public class CatalogPublicationVariantEntity {
    @EmbeddedId
    private CatalogPublicationVariantId id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "option_1_value", length = 50)
    private @Nullable String option1Value;

    @Column(name = "option_2_value", length = 50)
    private @Nullable String option2Value;

    @Column(name = "amount", columnDefinition = "catalog_inr_amount")
    private @Nullable BigDecimal amount;

    @Column(name = "sellable", nullable = false)
    private boolean sellable;

    protected CatalogPublicationVariantEntity() {}

    public CatalogPublicationVariantEntity(
            CatalogPublicationVariantId id,
            UUID productId,
            List<String> optionValues,
            @Nullable BigDecimal amount,
            boolean sellable) {
        this.id = id;
        this.productId = productId;
        this.option1Value = optionValues.isEmpty() ? null : optionValues.getFirst();
        this.option2Value = optionValues.size() < 2 ? null : optionValues.get(1);
        this.amount = amount;
        this.sellable = sellable;
    }

    public CatalogPublicationVariantId id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public List<String> optionValues() {
        if (option1Value == null) {
            return List.of();
        }
        if (option2Value == null) {
            return List.of(option1Value);
        }
        return List.of(option1Value, option2Value);
    }

    @Nullable
    public BigDecimal amount() {
        return amount;
    }

    public boolean sellable() {
        return sellable;
    }
}
