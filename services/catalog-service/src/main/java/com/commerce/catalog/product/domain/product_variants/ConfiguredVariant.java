package com.commerce.catalog.product.domain.product_variants;

import com.commerce.catalog.product.domain.InrMoney;
import com.commerce.catalog.product.domain.OptionValues;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ConfiguredVariant(
        VariantId variantId,
        OptionValues optionValues,
        boolean sellable,
        @Nullable InrMoney price) {
    public ConfiguredVariant {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(optionValues, "optionValues");
    }
}
