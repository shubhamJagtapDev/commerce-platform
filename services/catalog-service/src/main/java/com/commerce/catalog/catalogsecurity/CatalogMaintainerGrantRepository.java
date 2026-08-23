package com.commerce.catalog.catalogsecurity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CatalogMaintainerGrantRepository extends JpaRepository<CatalogMaintainerGrantEntity, UUID> {
    Optional<CatalogMaintainerGrantEntity> findByIssuerAndSubject(String issuer, String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select grant
            from CatalogMaintainerGrantEntity grant
            where grant.issuer = :issuer and grant.subject = :subject and grant.status = 'ACTIVE'
            """)
    Optional<CatalogMaintainerGrantEntity> lockActive(@Param("issuer") String issuer, @Param("subject") String subject);
}
