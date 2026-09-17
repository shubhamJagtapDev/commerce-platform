package com.commerce.catalog.product.persistence.product;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogProductRepository extends JpaRepository<CatalogProductEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from CatalogProductEntity product where product.id = :id")
    Optional<CatalogProductEntity> lockById(@Param("id") UUID id);

    @Query(value = """
                    select
                        product.id as productId,
                        product.version as version,
                        product.state::text as state,
                        draft.name as candidateName,
                        product.current_publication_id as currentPublicationId
                    from catalog_product product
                    left join catalog_product_draft draft
                        on draft.product_id = product.id
                    where (:state is null or product.state = cast(:state as catalog_product_state))
                        and (:after is null or product.id > cast(:after as uuid))
                    order by product.id
                    limit :limit
                    """, nativeQuery = true)
    List<MaintainerProductProjection> listForMaintainer(
            @Param("state") @Nullable String state, @Param("after") @Nullable UUID after, @Param("limit") int limit);
}
