package com.commerce.catalog.product.persistence.draft;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogDraftVariantRepository extends JpaRepository<CatalogDraftVariantEntity, CatalogDraftVariantId> {
    List<CatalogDraftVariantEntity> findByIdProductIdOrderByIdVariantId(UUID productId);

    void deleteByIdProductId(UUID productId);
}
