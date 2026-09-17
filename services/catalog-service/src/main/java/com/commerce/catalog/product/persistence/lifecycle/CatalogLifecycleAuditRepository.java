package com.commerce.catalog.product.persistence.lifecycle;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogLifecycleAuditRepository extends JpaRepository<CatalogLifecycleAuditEntity, UUID> {}
