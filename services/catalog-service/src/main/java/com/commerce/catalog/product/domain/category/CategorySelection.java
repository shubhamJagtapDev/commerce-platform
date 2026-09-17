package com.commerce.catalog.product.domain.category;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record CategorySelection(
        CategoryId mainCategoryId, @Nullable CategoryId subcategoryId) {
    public CategorySelection {
        Objects.requireNonNull(mainCategoryId, "mainCategoryId");
    }
}
