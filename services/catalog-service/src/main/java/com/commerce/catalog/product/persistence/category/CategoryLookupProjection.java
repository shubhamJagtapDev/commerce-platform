package com.commerce.catalog.product.persistence.category;

import java.util.UUID;

public interface CategoryLookupProjection {
    UUID getCategoryId();

    String getName();

    String getLevel();

    UUID getMainCategoryId();
}
