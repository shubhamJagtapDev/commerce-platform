package com.commerce.catalog.product.persistence.category;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "catalog_subcategory")
public class CatalogSubcategoryEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "main_category_id", nullable = false)
    private UUID mainCategoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected CatalogSubcategoryEntity() {}
}
