package com.commerce.catalog.product.persistence.category;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CatalogCategoryLookupRepository extends Repository<CatalogMainCategoryEntity, UUID> {
    @Query(value = """
                    select *
                    from (
                        select
                            main_category.id as categoryId,
                            main_category.name as name,
                            'MAIN' as level,
                            main_category.id as mainCategoryId
                        from catalog_main_category main_category
                        union all
                        select
                            subcategory.id as categoryId,
                            subcategory.name as name,
                            'SUB' as level,
                            subcategory.main_category_id as mainCategoryId
                        from catalog_subcategory subcategory
                    ) category
                    where (:after is null or category.categoryId > cast(:after as uuid))
                    order by category.categoryId
                    limit :limit
                    """, nativeQuery = true)
    List<CategoryLookupProjection> listCategoryPage(@Param("after") @Nullable UUID after, @Param("limit") int limit);
}
