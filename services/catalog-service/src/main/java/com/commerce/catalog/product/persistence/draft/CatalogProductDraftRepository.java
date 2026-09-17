package com.commerce.catalog.product.persistence.draft;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogProductDraftRepository extends JpaRepository<CatalogProductDraftEntity, UUID> {
    Optional<CatalogProductDraftEntity> findByProductId(UUID productId);
}
