package com.commerce.catalog.catalogsecurity.repositories;

import com.commerce.catalog.catalogsecurity.models.CatalogAuthorizationProbeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAuthorizationProbeRepository extends JpaRepository<CatalogAuthorizationProbeEntity, UUID> {}
