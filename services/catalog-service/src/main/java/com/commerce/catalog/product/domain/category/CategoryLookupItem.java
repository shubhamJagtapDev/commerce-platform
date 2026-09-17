package com.commerce.catalog.product.domain.category;

import com.commerce.catalog.product.domain.TextFields;
import java.util.Objects;

public record CategoryLookupItem(CategoryId categoryId, String name, CategoryLevel level, CategoryId mainCategoryId) {
    public CategoryLookupItem {
        Objects.requireNonNull(categoryId, "categoryId");
        name = TextFields.normalizeRequired("category.name", name, 100);
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(mainCategoryId, "mainCategoryId");
    }
}
