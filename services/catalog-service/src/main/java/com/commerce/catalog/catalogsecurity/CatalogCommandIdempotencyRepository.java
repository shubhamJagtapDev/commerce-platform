package com.commerce.catalog.catalogsecurity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CatalogCommandIdempotencyRepository extends JpaRepository<CatalogCommandIdempotencyEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from CatalogCommandIdempotencyEntity claim
            where claim.issuer = :issuer
              and claim.subject = :subject
              and claim.operationCode = :operationCode
              and claim.keyHash = :keyHash
            """)
    Optional<CatalogCommandIdempotencyEntity> lockByScope(
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("operationCode") String operationCode,
            @Param("keyHash") byte[] keyHash);
}
