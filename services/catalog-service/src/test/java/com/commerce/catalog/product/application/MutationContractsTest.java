package com.commerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.product_models.ImageUrls;
import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MutationContractsTest {
    private static final ProductId PRODUCT_ID = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final VariantId VARIANT_ID = new VariantId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

    @Test
    void validMutationShapesExposeTheirSemanticOperation() {
        PublicationCandidate candidate = candidate();

        assertThat(new CreateDraftMutation(candidate).operation()).isEqualTo(ProductLifecycleOperation.CREATE_DRAFT);
        assertThat(new ReplaceCandidateMutation(PRODUCT_ID, 2, candidate).operation())
                .isEqualTo(ProductLifecycleOperation.REPLACE_CANDIDATE);
        assertThat(new LifecycleMutation(ProductLifecycleOperation.PUBLISH, PRODUCT_ID, null, 2).operation())
                .isEqualTo(ProductLifecycleOperation.PUBLISH);
        assertThat(new LifecycleMutation(ProductLifecycleOperation.WITHDRAW_VARIANT, PRODUCT_ID, VARIANT_ID, 3)
                        .variantId())
                .isEqualTo(VARIANT_ID);
    }

    @Test
    void lifecycleMutationRejectsInvalidOperationVariantAndVersionShapes() {
        assertThatThrownBy(() -> new LifecycleMutation(ProductLifecycleOperation.CREATE_DRAFT, PRODUCT_ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleMutation(ProductLifecycleOperation.WITHDRAW_VARIANT, PRODUCT_ID, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleMutation(ProductLifecycleOperation.PUBLISH, PRODUCT_ID, VARIANT_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LifecycleMutation(ProductLifecycleOperation.PUBLISH, PRODUCT_ID, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplaceCandidateMutation(PRODUCT_ID, -1, candidate()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idempotencyKeyEnforcesAllowedCharactersAndRedactsItsValue() {
        String rawKey = "safe_Key-1234567";
        IdempotencyKey key = new IdempotencyKey(rawKey);

        assertThat(key.toString()).doesNotContain(rawKey).contains("REDACTED");
        assertThatThrownBy(() -> new IdempotencyKey("too-short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("invalid key value")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("a".repeat(129))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionResultDerivesCreateLocationAndPreservesOriginalStatusOnReplay() {
        Instant appliedAt = Instant.parse("2026-09-16T00:00:00Z");
        CommandOutcome outcome = new CommandOutcome(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                ProductLifecycleOperation.CREATE_DRAFT,
                PRODUCT_ID,
                null,
                0,
                ProductCommandResultState.DRAFT,
                null,
                appliedAt);

        CommandExecutionResult replay = new CommandExecutionResult(outcome, 201, true);

        assertThat(replay.originalHttpStatus()).isEqualTo(201);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.location())
                .isEqualTo("/api/v1/catalog/maintenance/products/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThatThrownBy(() -> new CommandOutcome(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        ProductLifecycleOperation.CREATE_DRAFT,
                        PRODUCT_ID,
                        null,
                        0,
                        ProductCommandResultState.DRAFT,
                        null,
                        Instant.parse("2026-09-16T00:00:00.123456789Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preparedDigestsDefensivelyCopyArrays() {
        byte[] keyHash = new byte[32];
        byte[] fingerprint = new byte[32];
        PreparedLifecycleDigest digest = new PreparedLifecycleDigest("key-1", keyHash, fingerprint);

        keyHash[0] = 1;
        fingerprint[0] = 1;
        byte[] returnedKeyHash = digest.keyHash();
        byte[] returnedFingerprint = digest.requestFingerprint();
        returnedKeyHash[1] = 1;
        returnedFingerprint[1] = 1;

        assertThat(digest.keyHash()).containsOnly(0);
        assertThat(digest.requestFingerprint()).containsOnly(0);
        assertThat(digest.toString()).doesNotContain("[0, 0").contains("REDACTED");
    }

    private static PublicationCandidate candidate() {
        return new PublicationCandidate(
                new ProductContent("Product", "Description", ImageUrls.none(), null),
                new CategorySelection(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001")), null),
                OptionDimensions.none(),
                List.of(new ConfiguredVariant(VARIANT_ID, OptionValues.none(), false, null)));
    }
}
