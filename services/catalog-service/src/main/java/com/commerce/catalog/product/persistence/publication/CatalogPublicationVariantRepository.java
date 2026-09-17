package com.commerce.catalog.product.persistence.publication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogPublicationVariantRepository
        extends JpaRepository<CatalogPublicationVariantEntity, CatalogPublicationVariantId> {
    List<CatalogPublicationVariantEntity> findByProductIdAndIdPublicationIdOrderByIdVariantId(
            UUID productId, UUID publicationId);
}
