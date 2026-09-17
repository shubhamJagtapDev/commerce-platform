package com.commerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.catalog.product.application.CommandExecutionResult;
import com.commerce.catalog.product.application.CommandOutcome;
import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.config.CatalogLifecycleProperties;
import com.commerce.catalog.product.config.CatalogLifecycleProperties.HmacKeyRingProperties;
import com.commerce.catalog.product.config.HmacKeyProperties;
import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.commerce.catalog.product.persistence.lifecycle.CatalogLifecycleCommandRepository;
import com.commerce.catalog.product.persistence.lifecycle.CatalogLifecycleReceiptStore;
import com.commerce.catalog.product.receipt.LifecycleReceiptIntegrityException;
import com.commerce.catalog.product.receipt.LifecycleReceiptReplayPolicy;
import com.commerce.catalog.product.receipt.ReceiptReplayDecision;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
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
@Import({
    CatalogLifecycleReceiptStore.class,
    LifecycleReceiptReplayPolicy.class,
    CatalogLifecycleReceiptStoreTest.Configuration.class
})
@TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers(disabledWithoutDocker = true)
class CatalogLifecycleReceiptStoreTest {
    private static final UUID GRANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ProductId PRODUCT_ID = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00.123456Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private CatalogLifecycleReceiptStore store;

    @Autowired
    private CatalogLifecycleCommandRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void receiptRoundTripPreservesEveryOutcomeFieldAndReplayDoesNotMutateExpiry() {
        PreparedLifecycleDigest digest = digest("active", (byte) 1, (byte) 2);
        UUID publicationId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        VariantId variantId = new VariantId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        CommandOutcome outcome = new CommandOutcome(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                ProductLifecycleOperation.WITHDRAW_VARIANT,
                PRODUCT_ID,
                variantId,
                7,
                ProductCommandResultState.PUBLISHED,
                publicationId,
                NOW);
        store.append(GRANT_ID, digest, new CommandExecutionResult(outcome, 200, false));
        repository.flush();
        entityManager.clear();
        Instant originalExpiry = jdbcTemplate.queryForObject(
                "select expires_at from catalog_lifecycle_command where id = ?", Instant.class, outcome.commandId());

        ReceiptReplayDecision decision = store.resolve(
                GRANT_ID, ProductLifecycleOperation.WITHDRAW_VARIANT, List.of(digest), NOW.plusSeconds(60));
        entityManager.flush();

        assertThat(decision).isInstanceOfSatisfying(ReceiptReplayDecision.Replay.class, replay -> {
            assertThat(replay.result().outcome()).isEqualTo(outcome);
            assertThat(replay.result().originalHttpStatus()).isEqualTo(200);
            assertThat(replay.result().location()).isNull();
        });
        assertThat(jdbcTemplate.queryForObject(
                        "select expires_at from catalog_lifecycle_command where id = ?",
                        Instant.class,
                        outcome.commandId()))
                .isEqualTo(originalExpiry);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void discardReceiptWithNullableCorrelationsReplaysWithoutAProductRow() {
        PreparedLifecycleDigest digest = digest("active", (byte) 3, (byte) 4);
        CommandOutcome outcome = outcome(
                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc1"),
                ProductLifecycleOperation.DISCARD,
                3,
                ProductCommandResultState.DISCARDED,
                NOW);
        store.append(GRANT_ID, digest, new CommandExecutionResult(outcome, 200, false));
        repository.flush();
        entityManager.clear();

        ReceiptReplayDecision decision =
                store.resolve(GRANT_ID, ProductLifecycleOperation.DISCARD, List.of(digest), NOW.plusSeconds(1));

        assertThat(decision).isInstanceOfSatisfying(ReceiptReplayDecision.Replay.class, replay -> {
            assertThat(replay.result().outcome().resultState()).isEqualTo(ProductCommandResultState.DISCARDED);
            assertThat(replay.result().outcome().variantId()).isNull();
            assertThat(replay.result().outcome().publicationId()).isNull();
        });
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from catalog_product where id = ?", Long.class, PRODUCT_ID.value()))
                .isZero();
    }

    @Test
    void differentFingerprintConflictsAndRetainedKeyCanResolveTheOriginalReceipt() {
        PreparedLifecycleDigest retained = digest("old-key", (byte) 5, (byte) 6);
        store.append(
                GRANT_ID,
                retained,
                new CommandExecutionResult(
                        outcome(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc2"),
                                ProductLifecycleOperation.PUBLISH,
                                1,
                                ProductCommandResultState.PUBLISHED,
                                NOW),
                        200,
                        false));
        repository.flush();
        entityManager.clear();

        PreparedLifecycleDigest active = digest("active", (byte) 7, (byte) 8);
        PreparedLifecycleDigest conflictingRetained = digest("old-key", (byte) 5, (byte) 9);
        assertThat(store.resolve(
                        GRANT_ID,
                        ProductLifecycleOperation.PUBLISH,
                        List.of(active, conflictingRetained),
                        NOW.plusSeconds(1)))
                .isEqualTo(ReceiptReplayDecision.Conflict.INSTANCE);
        assertThat(store.resolve(
                        GRANT_ID, ProductLifecycleOperation.PUBLISH, List.of(active, retained), NOW.plusSeconds(1)))
                .isInstanceOf(ReceiptReplayDecision.Replay.class);
    }

    @Test
    void expiredReceiptIsDeletedAndFlushedBeforeTheScopeIsReused() {
        PreparedLifecycleDigest digest = digest("active", (byte) 10, (byte) 11);
        Instant expiredAppliedAt = NOW.minus(Duration.ofHours(25));
        store.append(
                GRANT_ID,
                digest,
                new CommandExecutionResult(
                        outcome(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc3"),
                                ProductLifecycleOperation.PUBLISH,
                                1,
                                ProductCommandResultState.PUBLISHED,
                                expiredAppliedAt),
                        200,
                        false));
        repository.flush();
        entityManager.clear();

        assertThat(store.resolve(GRANT_ID, ProductLifecycleOperation.PUBLISH, List.of(digest), NOW))
                .isEqualTo(ReceiptReplayDecision.Fresh.INSTANCE);
        assertThat(repository.count()).isZero();

        store.append(
                GRANT_ID,
                digest,
                new CommandExecutionResult(
                        outcome(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc4"),
                                ProductLifecycleOperation.PUBLISH,
                                2,
                                ProductCommandResultState.PUBLISHED,
                                NOW),
                        200,
                        false));
        repository.flush();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void multipleKeyVersionMatchesAreRejectedAsAnIntegrityFailure() {
        PreparedLifecycleDigest first = digest("a-key", (byte) 12, (byte) 13);
        PreparedLifecycleDigest second = digest("b-key", (byte) 14, (byte) 15);
        store.append(
                GRANT_ID,
                first,
                new CommandExecutionResult(
                        outcome(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc5"),
                                ProductLifecycleOperation.PUBLISH,
                                1,
                                ProductCommandResultState.PUBLISHED,
                                NOW),
                        200,
                        false));
        store.append(
                GRANT_ID,
                second,
                new CommandExecutionResult(
                        outcome(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-ccccccccccc6"),
                                ProductLifecycleOperation.PUBLISH,
                                2,
                                ProductCommandResultState.PUBLISHED,
                                NOW.plusSeconds(1)),
                        200,
                        false));
        repository.flush();
        entityManager.clear();

        assertThatThrownBy(() -> store.resolve(
                        GRANT_ID, ProductLifecycleOperation.PUBLISH, List.of(second, first), NOW.plusSeconds(2)))
                .isInstanceOf(LifecycleReceiptIntegrityException.class);
    }

    @Test
    void receiptSchemaContainsOnlyDigestsAndTypedOutcomeRatherThanRawSensitiveValues() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in ('catalog_lifecycle_command', 'catalog_lifecycle_audit')
                """, String.class);

        assertThat(columns)
                .doesNotContain(
                        "idempotency_key",
                        "issuer",
                        "subject",
                        "actor",
                        "request_body",
                        "request_content",
                        "token",
                        "url");
    }

    private static CommandOutcome outcome(
            UUID commandId,
            ProductLifecycleOperation operation,
            long resultVersion,
            ProductCommandResultState resultState,
            Instant appliedAt) {
        return new CommandOutcome(commandId, operation, PRODUCT_ID, null, resultVersion, resultState, null, appliedAt);
    }

    private static PreparedLifecycleDigest digest(String keyId, byte keyByte, byte fingerprintByte) {
        byte[] keyHash = new byte[32];
        keyHash[0] = keyByte;
        byte[] fingerprint = new byte[32];
        fingerprint[0] = fingerprintByte;
        return new PreparedLifecycleDigest(keyId, keyHash, fingerprint);
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        CatalogLifecycleProperties catalogLifecycleProperties() {
            String material = Base64.getEncoder().encodeToString(new byte[32]);
            HmacKeyProperties key = new HmacKeyProperties("unused", material);
            return new CatalogLifecycleProperties(
                    Duration.ofHours(24),
                    new HmacKeyRingProperties(key, List.of()),
                    new HmacKeyRingProperties(key, List.of()));
        }
    }
}
