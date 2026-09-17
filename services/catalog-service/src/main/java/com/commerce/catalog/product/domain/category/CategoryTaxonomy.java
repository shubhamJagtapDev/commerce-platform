package com.commerce.catalog.product.domain.category;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CategoryTaxonomy(Set<CategoryId> mainCategoryIds, Map<CategoryId, CategoryId> subcategoryParentsById) {
    public CategoryTaxonomy {
        mainCategoryIds = Set.copyOf(Objects.requireNonNull(mainCategoryIds, "mainCategoryIds"));
        subcategoryParentsById = Map.copyOf(Objects.requireNonNull(subcategoryParentsById, "subcategoryParentsById"));
    }

    public boolean containsMain(CategoryId categoryId) {
        return mainCategoryIds.contains(categoryId);
    }

    public boolean containsSubcategory(CategoryId mainCategoryId, CategoryId subcategoryId) {
        return mainCategoryId.equals(subcategoryParentsById.get(subcategoryId));
    }
}
