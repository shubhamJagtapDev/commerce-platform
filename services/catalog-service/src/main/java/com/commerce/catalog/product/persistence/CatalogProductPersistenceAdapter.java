package com.commerce.catalog.product.persistence;

import com.commerce.catalog.product.domain.InrMoney;
import com.commerce.catalog.product.domain.MaintainerProductListItem;
import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategoryLevel;
import com.commerce.catalog.product.domain.category.CategoryLookupItem;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.product_models.ImageUrls;
import com.commerce.catalog.product.domain.product_models.LongDescriptionDocument;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductRoot;
import com.commerce.catalog.product.domain.product_models.ProductState;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_publication.PublicationPlan;
import com.commerce.catalog.product.domain.product_publication.PublicationSnapshot;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.commerce.catalog.product.domain.product_variants.VariantIdentity;
import com.commerce.catalog.product.persistence.category.CatalogCategoryLookupRepository;
import com.commerce.catalog.product.persistence.draft.CatalogDraftVariantEntity;
import com.commerce.catalog.product.persistence.draft.CatalogDraftVariantId;
import com.commerce.catalog.product.persistence.draft.CatalogDraftVariantRepository;
import com.commerce.catalog.product.persistence.draft.CatalogProductDraftEntity;
import com.commerce.catalog.product.persistence.draft.CatalogProductDraftRepository;
import com.commerce.catalog.product.persistence.listing.CatalogPublicListingEntity;
import com.commerce.catalog.product.persistence.listing.CatalogPublicListingRepository;
import com.commerce.catalog.product.persistence.product.CatalogProductEntity;
import com.commerce.catalog.product.persistence.product.CatalogProductRepository;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationEntity;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationRepository;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantEntity;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantId;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantRepository;
import com.commerce.catalog.product.persistence.variant.CatalogVariantEntity;
import com.commerce.catalog.product.persistence.variant.CatalogVariantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogProductPersistenceAdapter {
    private static final int MAX_QUERY_LIMIT = 51;

    private final CatalogProductRepository productRepository;
    private final CatalogVariantRepository variantRepository;
    private final CatalogProductDraftRepository draftRepository;
    private final CatalogDraftVariantRepository draftVariantRepository;
    private final CatalogPublicationRepository publicationRepository;
    private final CatalogPublicationVariantRepository publicationVariantRepository;
    private final CatalogPublicListingRepository listingRepository;
    private final CatalogCategoryLookupRepository categoryLookupRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    CatalogProductPersistenceAdapter(
            CatalogProductRepository productRepository,
            CatalogVariantRepository variantRepository,
            CatalogProductDraftRepository draftRepository,
            CatalogDraftVariantRepository draftVariantRepository,
            CatalogPublicationRepository publicationRepository,
            CatalogPublicationVariantRepository publicationVariantRepository,
            CatalogPublicListingRepository listingRepository,
            CatalogCategoryLookupRepository categoryLookupRepository,
            EntityManager entityManager,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.draftRepository = draftRepository;
        this.draftVariantRepository = draftVariantRepository;
        this.publicationRepository = publicationRepository;
        this.publicationVariantRepository = publicationVariantRepository;
        this.listingRepository = listingRepository;
        this.categoryLookupRepository = categoryLookupRepository;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    public ProductRoot createDraft(ProductId productId, UUID commandId, PublicationCandidate candidate, Instant now) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(now, "now");

        CatalogProductEntity product = new CatalogProductEntity(productId.value(), commandId, now);
        entityManager.persist(product);
        persistVariantIdentities(productId, candidate.variants(), now);
        persistCandidate(productId, candidate);
        return toRoot(product);
    }

    public Optional<ProductRoot> lockRoot(ProductId productId) {
        Objects.requireNonNull(productId, "productId");
        return productRepository.lockById(productId.value()).map(this::toRoot);
    }

    public void replaceCandidateForLockedProduct(ProductId productId, PublicationCandidate candidate) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(candidate, "candidate");

        draftVariantRepository.deleteByIdProductId(productId.value());
        draftRepository.deleteById(productId.value());
        entityManager.flush();
        persistCandidate(productId, candidate);
    }

    public Optional<PublicationCandidate> loadCandidate(ProductId productId) {
        Objects.requireNonNull(productId, "productId");
        return draftRepository.findByProductId(productId.value()).map(draft -> {
            List<CatalogDraftVariantEntity> variants =
                    draftVariantRepository.findByIdProductIdOrderByIdVariantId(productId.value());
            return toCandidate(draft, variants);
        });
    }

    public Optional<PublicationSnapshot> loadPublication(ProductId productId, UUID publicationId) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(publicationId, "publicationId");

        return publicationRepository
                .findByProductIdAndId(productId.value(), publicationId)
                .map(publication -> {
                    List<CatalogPublicationVariantEntity> variants =
                            publicationVariantRepository.findByProductIdAndIdPublicationIdOrderByIdVariantId(
                                    productId.value(), publicationId);
                    return toPublicationSnapshot(publication, variants);
                });
    }

    public List<VariantIdentity> lockVariantIdentities(ProductId productId, Collection<VariantId> variantIds) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(variantIds, "variantIds");

        List<UUID> orderedIds =
                variantIds.stream().map(VariantId::value).distinct().sorted().toList();
        return variantRepository.lockOwnedByIdInUuidOrder(productId.value(), orderedIds).stream()
                .map(entity -> new VariantIdentity(
                        productId, new VariantId(entity.id()), entity.firstPublicationId(), entity.createdAt()))
                .toList();
    }

    public PublicationSnapshot createSealedPublicationFromPlan(
            ProductId productId, UUID publicationId, long productVersion, PublicationPlan plan, Instant appliedAt) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(appliedAt, "appliedAt");

        PublicationCandidate candidate = plan.candidate();
        String validatedPublicationShortDescription = Objects.requireNonNull(
                candidate.content().shortDescription(), "validated publication shortDescription");
        String validatedPublicationName =
                Objects.requireNonNull(candidate.content().name(), "validated publication name");
        CatalogPublicationEntity publication = new CatalogPublicationEntity(
                publicationId,
                productId.value(),
                productVersion,
                validatedPublicationName,
                validatedPublicationShortDescription,
                candidate.category().mainCategoryId().value(),
                categoryUuid(candidate.category().subcategoryId()),
                candidate.content().imageUrls().values(),
                toJsonNode(candidate.content().longDescription()),
                candidate.optionDimensions().names(),
                plan.sellableCount(),
                appliedAt);
        entityManager.persist(publication);

        for (ConfiguredVariant variant : candidate.variants()) {
            entityManager.persist(new CatalogPublicationVariantEntity(
                    new CatalogPublicationVariantId(
                            publicationId, variant.variantId().value()),
                    productId.value(),
                    variant.optionValues().values(),
                    amount(variant.price()),
                    variant.sellable()));
        }
        entityManager.flush();

        publication.seal();
        entityManager.flush();

        List<VariantIdentity> variantIdentities = lockVariantIdentities(
                productId,
                candidate.variants().stream()
                        .filter(ConfiguredVariant::sellable)
                        .map(ConfiguredVariant::variantId)
                        .toList());
        for (VariantIdentity variantIdentity : variantIdentities) {
            CatalogVariantEntity entity = entityManager.getReference(
                    CatalogVariantEntity.class, variantIdentity.variantId().value());
            entity.markFirstPublication(publicationId);
        }

        listingRepository.save(new CatalogPublicListingEntity(
                productId.value(),
                publicationId,
                validatedPublicationName,
                validatedPublicationShortDescription,
                candidate.category().mainCategoryId().value(),
                categoryUuid(candidate.category().subcategoryId()),
                plan.representativePrice().amount(),
                plan.priceFrom(),
                plan.sellableCount()));

        List<CatalogPublicationVariantEntity> variants =
                publicationVariantRepository.findByProductIdAndIdPublicationIdOrderByIdVariantId(
                        productId.value(), publicationId);
        return toPublicationSnapshot(publication, variants);
    }

    public void markLockedRootPublished(ProductId productId, UUID publicationId, UUID commandId, Instant now) {
        CatalogProductEntity product = lockRootEntity(productId);
        product.markPublished(publicationId, commandId, now);
    }

    public List<MaintainerProductListItem> listProducts(
            @Nullable ProductState state, @Nullable ProductId after, int limit) {
        validateLimit(limit);
        return productRepository
                .listForMaintainer(state == null ? null : state.name(), after == null ? null : after.value(), limit)
                .stream()
                .map(row -> new MaintainerProductListItem(
                        new ProductId(row.getProductId()),
                        row.getVersion(),
                        ProductState.valueOf(row.getState()),
                        row.getCandidateName(),
                        row.getCurrentPublicationId()))
                .toList();
    }

    public List<CategoryLookupItem> listCategories(@Nullable CategoryId after, int limit) {
        validateLimit(limit);
        return categoryLookupRepository.listCategoryPage(after == null ? null : after.value(), limit).stream()
                .map(row -> new CategoryLookupItem(
                        new CategoryId(row.getCategoryId()),
                        row.getName(),
                        CategoryLevel.valueOf(row.getLevel()),
                        new CategoryId(row.getMainCategoryId())))
                .toList();
    }

    private void persistVariantIdentities(ProductId productId, List<ConfiguredVariant> variants, Instant now) {
        variants.stream()
                .sorted(Comparator.comparing(variant -> variant.variantId().value()))
                .map(variant -> new CatalogVariantEntity(variant.variantId().value(), productId.value(), now))
                .forEach(entityManager::persist);
    }

    private void persistCandidate(ProductId productId, PublicationCandidate candidate) {
        entityManager.persist(new CatalogProductDraftEntity(
                productId.value(),
                candidate.content().name(),
                candidate.content().shortDescription(),
                candidate.category().mainCategoryId().value(),
                categoryUuid(candidate.category().subcategoryId()),
                candidate.content().imageUrls().values(),
                toJsonNode(candidate.content().longDescription()),
                candidate.optionDimensions().names().isEmpty()
                        ? null
                        : candidate.optionDimensions().names().getFirst(),
                candidate.optionDimensions().names().size() < 2
                        ? null
                        : candidate.optionDimensions().names().get(1)));
        candidate.variants().stream()
                .sorted(Comparator.comparing(variant -> variant.variantId().value()))
                .map(variant -> new CatalogDraftVariantEntity(
                        new CatalogDraftVariantId(
                                productId.value(), variant.variantId().value()),
                        variant.optionValues().values(),
                        amount(variant.price()),
                        variant.sellable()))
                .forEach(entityManager::persist);
    }

    private CatalogProductEntity lockRootEntity(ProductId productId) {
        return productRepository
                .lockById(productId.value())
                .orElseThrow(() -> new IllegalArgumentException("product does not exist"));
    }

    private ProductRoot toRoot(CatalogProductEntity product) {
        return new ProductRoot(
                new ProductId(product.id()),
                product.state(),
                product.version(),
                product.lastCommandId(),
                product.currentPublicationId(),
                product.firstPublishedAt());
    }

    private PublicationCandidate toCandidate(
            CatalogProductDraftEntity draft, List<CatalogDraftVariantEntity> variants) {
        return new PublicationCandidate(
                new ProductContent(
                        draft.name(),
                        draft.shortDescription(),
                        new ImageUrls(draft.imageUrls()),
                        toLongDescriptionDocument(draft.longDescription())),
                new CategorySelection(new CategoryId(draft.mainCategoryId()), toCategoryId(draft.subcategoryId())),
                new OptionDimensions(draft.optionDimensions()),
                variants.stream()
                        .map(variant -> new ConfiguredVariant(
                                new VariantId(variant.id().variantId()),
                                new OptionValues(variant.optionValues()),
                                variant.sellable(),
                                toMoney(variant.amount())))
                        .toList());
    }

    private PublicationSnapshot toPublicationSnapshot(
            CatalogPublicationEntity publication, List<CatalogPublicationVariantEntity> variants) {
        PublicationCandidate candidate = new PublicationCandidate(
                new ProductContent(
                        publication.name(),
                        publication.shortDescription(),
                        new ImageUrls(publication.imageUrls()),
                        toLongDescriptionDocument(publication.longDescription())),
                new CategorySelection(
                        new CategoryId(publication.mainCategoryId()), toCategoryId(publication.subcategoryId())),
                new OptionDimensions(publication.optionDimensions()),
                variants.stream()
                        .map(variant -> new ConfiguredVariant(
                                new VariantId(variant.id().variantId()),
                                new OptionValues(variant.optionValues()),
                                variant.sellable(),
                                toMoney(variant.amount())))
                        .toList());
        return new PublicationSnapshot(
                publication.id(),
                new ProductId(publication.productId()),
                publication.productVersion(),
                candidate,
                candidate.variants());
    }

    private @Nullable JsonNode toJsonNode(@Nullable LongDescriptionDocument document) {
        if (document == null) {
            return null;
        }
        try {
            return objectMapper.readTree(document.jsonObject());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("longDescription must be valid JSON", exception);
        }
    }

    private @Nullable LongDescriptionDocument toLongDescriptionDocument(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        return new LongDescriptionDocument(node.toString());
    }

    private @Nullable BigDecimal amount(@Nullable InrMoney money) {
        return money == null ? null : money.amount();
    }

    private @Nullable InrMoney toMoney(@Nullable BigDecimal amount) {
        return amount == null ? null : new InrMoney(amount);
    }

    private @Nullable UUID categoryUuid(@Nullable CategoryId categoryId) {
        return categoryId == null ? null : categoryId.value();
    }

    private @Nullable CategoryId toCategoryId(@Nullable UUID categoryId) {
        return categoryId == null ? null : new CategoryId(categoryId);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_QUERY_LIMIT);
        }
    }
}
