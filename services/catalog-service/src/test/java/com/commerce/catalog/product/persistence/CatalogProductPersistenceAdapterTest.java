package com.commerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.catalog.product.domain.InrMoney;
import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategoryLevel;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.policies.PublicationPolicy;
import com.commerce.catalog.product.domain.product_models.ImageUrls;
import com.commerce.catalog.product.domain.product_models.LongDescriptionDocument;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductState;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_publication.PublicationPlan;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.commerce.catalog.product.domain.product_variants.VariantIdentity;
import com.commerce.catalog.product.persistence.product.CatalogProductEntity;
import com.commerce.catalog.product.persistence.product.CatalogProductRepository;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantEntity;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantId;
import com.commerce.catalog.product.persistence.publication.CatalogPublicationVariantRepository;
import com.commerce.catalog.product.persistence.variant.CatalogVariantEntity;
import com.commerce.catalog.product.persistence.variant.CatalogVariantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CatalogProductPersistenceAdapter.class, CatalogProductPersistenceAdapterTest.JsonConfiguration.class})
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.properties.hibernate.jdbc.batch_size=50",
            "spring.jpa.properties.hibernate.order_inserts=true",
            "spring.jpa.properties.hibernate.order_updates=true"
        })
@Testcontainers(disabledWithoutDocker = true)
class CatalogProductPersistenceAdapterTest {
    private static final CategoryId ELECTRONICS =
            new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final CategoryId TELEVISIONS =
            new CategoryId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private CatalogProductPersistenceAdapter adapter;

    @Autowired
    private CatalogProductRepository productRepository;

    @Autowired
    private CatalogPublicationVariantRepository publicationVariantRepository;

    @Autowired
    private CatalogVariantRepository variantRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createDraftRoundTripsJsonbAndExactMoneyWithoutJavaSerializationBlobs() {
        ProductId productId = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        PublicationCandidate candidate = completeCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));

        adapter.createDraft(productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), candidate, NOW);
        entityManager.flush();
        entityManager.clear();

        PublicationCandidate loaded = adapter.loadCandidate(productId).orElseThrow();
        LongDescriptionDocument longDescription =
                Objects.requireNonNull(loaded.content().longDescription());

        assertThat(longDescription.jsonObject()).contains("\"details\"");
        assertThat(loaded.variants())
                .extracting(variant ->
                        variant.price() == null ? null : variant.price().responseAmount())
                .containsExactly("1299.00", "999.00", null);
        assertThat(jdbcTemplate.queryForObject(
                        "select jsonb_typeof(image_urls) from catalog_product_draft where product_id = ?",
                        String.class,
                        productId.value()))
                .isEqualTo("array");
        assertThat(jdbcTemplate.queryForObject(
                        "select jsonb_typeof(long_description) from catalog_product_draft where product_id = ?",
                        String.class,
                        productId.value()))
                .isEqualTo("object");
    }

    @Test
    void rootLockUsesPessimisticWriteOnlyWhenRequested() {
        ProductId productId = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa001"));
        adapter.createDraft(
                productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb01"), singleVariantCandidate(), NOW);
        entityManager.flush();
        entityManager.clear();

        CatalogProductEntity locked =
                productRepository.lockById(productId.value()).orElseThrow();

        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void variantIdentitiesAreLockedAndReturnedInUuidOrder() {
        ProductId productId = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa002"));
        VariantId highest = new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        VariantId lowest = new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        VariantId middle = new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        adapter.createDraft(
                productId,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb02"),
                completeCandidate(highest.value(), lowest.value(), middle.value()),
                NOW);
        entityManager.flush();
        entityManager.clear();

        List<VariantIdentity> identities = adapter.lockVariantIdentities(productId, List.of(highest, lowest, middle));

        assertThat(identities).extracting(VariantIdentity::variantId).containsExactly(lowest, middle, highest);
    }

    @Test
    void categoryLookupUsesBoundedUuidSeekProjection() {
        List<CategoryLevel> firstLevels = adapter.listCategories(null, 5).stream()
                .map(category -> category.level())
                .toList();
        List<CategoryLevel> afterMainOnlyCategories =
                adapter
                        .listCategories(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000023")), 5)
                        .stream()
                        .map(category -> category.level())
                        .toList();

        assertThat(firstLevels).containsOnly(CategoryLevel.MAIN);
        assertThat(afterMainOnlyCategories).containsOnly(CategoryLevel.SUB);
    }

    @Test
    void productListUsesBoundedStateAndUuidSeekProjection() {
        ProductId first = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa011"));
        ProductId second = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa012"));
        adapter.createDraft(
                first, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb11"), singleVariantCandidate(), NOW);
        adapter.createDraft(
                second, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb12"), singleVariantCandidate(), NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.listProducts(ProductState.DRAFT, first, 2))
                .extracting(item -> item.productId())
                .containsExactly(second);
    }

    @Test
    void hibernateBatchingIsConfiguredForBoundedChildWrites() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);

        assertThat(sessionFactory.getSessionFactoryOptions().getJdbcBatchSize()).isEqualTo(50);
        assertThat(entityManager.getEntityManagerFactory().getProperties().get(AvailableSettings.ORDER_INSERTS))
                .isEqualTo("true");
    }

    @Test
    void sealedPublicationAndListingWorkThroughJpaMappings() {
        ProductId productId = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaa021"));
        UUID publicationId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccc21");
        PublicationCandidate candidate = completeCandidate(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        adapter.createDraft(productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbb21"), candidate, NOW);
        PublicationPlan plan = Objects.requireNonNull(new PublicationPolicy()
                .validate(candidate, PersistenceTestFixtures.taxonomy(), List.of())
                .plan());

        adapter.createSealedPublicationFromPlan(productId, publicationId, 1, plan, NOW);
        adapter.markLockedRootPublished(
                productId, publicationId, UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddd21"), NOW);
        entityManager.flush();

        assertThat(jdbcTemplate.queryForObject(
                        "select representative_amount from catalog_public_listing where product_id = ?",
                        String.class,
                        productId.value()))
                .isEqualTo("999.00");

        VariantId extraVariant = new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        variantRepository.save(new CatalogVariantEntity(extraVariant.value(), productId.value(), NOW));
        entityManager.flush();

        assertThatThrownBy(() -> {
                    publicationVariantRepository.save(new CatalogPublicationVariantEntity(
                            new CatalogPublicationVariantId(publicationId, extraVariant.value()),
                            productId.value(),
                            List.of("XXL"),
                            InrMoney.parse("1499.00", "INR").amount(),
                            true));
                    entityManager.flush();
                })
                .isInstanceOf(ConstraintViolationException.class);
    }

    private PublicationCandidate completeCandidate(UUID outOfOrderFirst, UUID outOfOrderSecond, UUID outOfOrderThird) {
        return new PublicationCandidate(
                new ProductContent(
                        "Television",
                        "Large 4K television",
                        new ImageUrls(List.of("https://images.example.test/products/tv-front.jpg")),
                        new LongDescriptionDocument("{\"details\":{\"resolution\":\"4K\"}}")),
                new CategorySelection(ELECTRONICS, TELEVISIONS),
                new OptionDimensions(List.of("Size")),
                List.of(
                        new ConfiguredVariant(
                                new VariantId(outOfOrderFirst), new OptionValues(List.of("65 inch")), false, null),
                        new ConfiguredVariant(
                                new VariantId(outOfOrderSecond),
                                new OptionValues(List.of("55 inch")),
                                true,
                                InrMoney.parse("1299.00", "INR")),
                        new ConfiguredVariant(
                                new VariantId(outOfOrderThird),
                                new OptionValues(List.of("43 inch")),
                                true,
                                InrMoney.parse("999.00", "INR"))));
    }

    private PublicationCandidate singleVariantCandidate() {
        return new PublicationCandidate(
                new ProductContent("Gift card", "Digital delivery", ImageUrls.none(), null),
                new CategorySelection(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000021")), null),
                OptionDimensions.none(),
                List.of(new ConfiguredVariant(
                        new VariantId(UUID.randomUUID()), OptionValues.none(), true, InrMoney.parse("500", "INR"))));
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
