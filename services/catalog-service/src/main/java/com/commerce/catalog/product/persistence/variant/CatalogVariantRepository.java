package com.commerce.catalog.product.persistence.variant;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogVariantRepository extends JpaRepository<CatalogVariantEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select variant
            from CatalogVariantEntity variant
            where variant.productId = :productId
              and variant.id in :variantIds
            order by variant.id
            """)
    List<CatalogVariantEntity> lockOwnedByIdInUuidOrder(
            @Param("productId") UUID productId, @Param("variantIds") Collection<UUID> variantIds);

    List<CatalogVariantEntity> findByProductIdOrderById(UUID productId);
}
