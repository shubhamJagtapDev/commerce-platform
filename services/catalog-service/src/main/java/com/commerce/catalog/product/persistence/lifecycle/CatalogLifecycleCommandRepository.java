package com.commerce.catalog.product.persistence.lifecycle;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogLifecycleCommandRepository extends JpaRepository<CatalogLifecycleCommandEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select command
            from CatalogLifecycleCommandEntity command
            where command.grantId = :grantId
              and command.operationCode = :operationCode
              and command.keyHash = :keyHash
            """)
    Optional<CatalogLifecycleCommandEntity> lockByReplayScope(
            @Param("grantId") UUID grantId,
            @Param("operationCode") String operationCode,
            @Param("keyHash") byte[] keyHash);
}
