package com.commerce.catalog.product.domain.product_publication;

import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import java.util.Objects;

public record PublishedVariantMeaning(
        VariantId variantId, OptionDimensions optionDimensions, OptionValues optionValues) {
    public PublishedVariantMeaning {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(optionDimensions, "optionDimensions");
        Objects.requireNonNull(optionValues, "optionValues");
    }
}
