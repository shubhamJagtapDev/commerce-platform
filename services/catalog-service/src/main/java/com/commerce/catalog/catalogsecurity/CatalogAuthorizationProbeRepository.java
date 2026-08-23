package com.commerce.catalog.catalogsecurity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogAuthorizationProbeRepository extends JpaRepository<CatalogAuthorizationProbeEntity, UUID> {}
