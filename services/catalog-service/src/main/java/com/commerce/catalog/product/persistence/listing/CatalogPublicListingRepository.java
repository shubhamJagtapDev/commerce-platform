package com.commerce.catalog.product.persistence.listing;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogPublicListingRepository extends JpaRepository<CatalogPublicListingEntity, UUID> {}
