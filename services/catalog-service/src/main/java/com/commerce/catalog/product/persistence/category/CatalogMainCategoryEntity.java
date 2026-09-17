package com.commerce.catalog.product.persistence.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "catalog_main_category")
public class CatalogMainCategoryEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected CatalogMainCategoryEntity() {}
}
