package com.commerce.catalog.product.domain.product_publication;

import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PublicationCandidate(
        ProductContent content,
        CategorySelection category,
        OptionDimensions optionDimensions,
        List<ConfiguredVariant> variants) {
    public static final int MAX_VARIANTS = 100;

    public PublicationCandidate {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(optionDimensions, "optionDimensions");
        Objects.requireNonNull(variants, "variants");
        if (variants.isEmpty() || variants.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("variants must contain between 1 and 100 values");
        }

        Set<VariantId> seenVariantIds = new HashSet<>();
        for (ConfiguredVariant variant : variants) {
            if (!seenVariantIds.add(variant.variantId())) {
                throw new IllegalArgumentException("variant IDs must be distinct");
            }
        }
        variants = List.copyOf(variants);
    }
}
