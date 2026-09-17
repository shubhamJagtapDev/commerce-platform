package com.commerce.catalog.product.domain.product_models;

import java.util.Objects;

import com.commerce.catalog.product.domain.TextFields;
import org.jspecify.annotations.Nullable;

public record ProductContent(
        @Nullable String name,
        @Nullable String shortDescription,
        ImageUrls imageUrls,
        @Nullable LongDescriptionDocument longDescription) {
    public static final int MAX_NAME_CODE_POINTS = 200;
    public static final int MAX_SHORT_DESCRIPTION_CODE_POINTS = 2_000;

    public ProductContent {
        name = TextFields.normalizeOptional("name", name, MAX_NAME_CODE_POINTS);
        shortDescription =
                TextFields.normalizeOptional("shortDescription", shortDescription, MAX_SHORT_DESCRIPTION_CODE_POINTS);
        Objects.requireNonNull(imageUrls, "imageUrls");
    }
}
