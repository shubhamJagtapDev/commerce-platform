package com.commerce.catalog.product.persistence;

import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategoryTaxonomy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class PersistenceTestFixtures {
    private PersistenceTestFixtures() {}

    static CategoryTaxonomy taxonomy() {
        CategoryId main = new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        CategoryId subcategory = new CategoryId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        return new CategoryTaxonomy(Set.of(main), Map.of(subcategory, main));
    }
}
