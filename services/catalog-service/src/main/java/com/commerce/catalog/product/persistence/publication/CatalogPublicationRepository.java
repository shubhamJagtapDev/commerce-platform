package com.commerce.catalog.product.persistence.publication;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogPublicationRepository extends JpaRepository<CatalogPublicationEntity, UUID> {
    Optional<CatalogPublicationEntity> findByProductIdAndId(UUID productId, UUID id);
}
