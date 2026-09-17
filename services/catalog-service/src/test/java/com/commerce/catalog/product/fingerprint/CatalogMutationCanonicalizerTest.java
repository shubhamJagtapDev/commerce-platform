package com.commerce.catalog.product.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.catalog.product.application.CreateDraftMutation;
import com.commerce.catalog.product.application.LifecycleMutation;
import com.commerce.catalog.product.application.ReplaceCandidateMutation;
import com.commerce.catalog.product.domain.InrMoney;
import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.product_models.ImageUrls;
import com.commerce.catalog.product.domain.product_models.LongDescriptionDocument;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogMutationCanonicalizerTest {
    private static final ProductId PRODUCT_ID = new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final VariantId FIRST_VARIANT =
            new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final VariantId SECOND_VARIANT =
            new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private final CatalogMutationCanonicalizer canonicalizer =
            new CatalogMutationCanonicalizer(new LongDescriptionCanonicalizer());

    @Test
    void normalizedTextVariantOrderAndJsonSpellingProduceTheSameFingerprintInput() {
        PublicationCandidate first = candidate(
                " Café ",
                List.of(secondVariant(), firstVariant()),
                "{\"z\":1.00,\"a\":[true,false]}",
                List.of("https://images.example.test/one", "https://images.example.test/two"));
        PublicationCandidate second = candidate(
                "Cafe\u0301",
                List.of(firstVariant(), secondVariant()),
                "{ \"a\" : [true, false], \"z\" : 1e0 }",
                List.of("https://images.example.test/one", "https://images.example.test/two"));

        assertThat(canonicalizer.canonicalize(new CreateDraftMutation(first)))
                .isEqualTo(canonicalizer.canonicalize(new CreateDraftMutation(second)));
    }

    @Test
    void createDraftCanonicalBytesAreFrozen() {
        PublicationCandidate candidate = candidate(
                "Café",
                List.of(secondVariant(), firstVariant()),
                "{\"z\":1.00,\"a\":[true,false]}",
                List.of("https://images.example.test/one", "https://images.example.test/two"));

        String canonical =
                new String(canonicalizer.canonicalize(new CreateDraftMutation(candidate)), StandardCharsets.UTF_8);

        assertThat(canonical)
                .isEqualTo("{\"format\":\"catalog-product-mutation/v1\",\"operation\":\"CREATE_DRAFT\","
                        + "\"productId\":null,\"variantId\":null,\"expectedVersion\":null,\"candidate\":{"
                        + "\"name\":\"Café\",\"shortDescription\":\"Description\","
                        + "\"mainCategoryId\":\"10000000-0000-0000-0000-000000000001\","
                        + "\"subcategoryId\":null,\"imageUrls\":[\"https://images.example.test/one\","
                        + "\"https://images.example.test/two\"],\"longDescription\":{\"a\":[true,false],\"z\":1},"
                        + "\"optionDimensions\":[\"Size\",\"Color\"],\"variants\":[{"
                        + "\"variantId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"optionValues\":[\"Small\",\"Blue\"],\"sellable\":true,"
                        + "\"price\":{\"amount\":\"10.00\",\"currency\":\"INR\"}},{"
                        + "\"variantId\":\"00000000-0000-0000-0000-000000000002\","
                        + "\"optionValues\":[\"Large\",\"Red\"],\"sellable\":false,\"price\":null}]}}");
    }

    @Test
    void operationTargetVersionAndOrderedContentChangesAlterCanonicalBytes() {
        PublicationCandidate base = candidate(
                "Café",
                List.of(firstVariant(), secondVariant()),
                "{\"items\":[1,2]}",
                List.of("https://images.example.test/one", "https://images.example.test/two"));
        byte[] replacement = canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, base));

        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(
                        new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaab")), 4, base)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 5, base)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(
                        new LifecycleMutation(ProductLifecycleOperation.PUBLISH, PRODUCT_ID, null, 4)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(
                        PRODUCT_ID,
                        4,
                        candidate(
                                "Café",
                                List.of(firstVariant(), secondVariant()),
                                "{\"items\":[2,1]}",
                                List.of("https://images.example.test/one", "https://images.example.test/two")))))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(
                        PRODUCT_ID,
                        4,
                        candidate(
                                "Café",
                                List.of(firstVariant(), secondVariant()),
                                "{\"items\":[1,2]}",
                                List.of("https://images.example.test/two", "https://images.example.test/one")))))
                .isNotEqualTo(replacement);
    }

    @Test
    void optionPositionsVariantTargetSellabilityAndPriceRemainFingerprintSignificant() {
        PublicationCandidate base = candidate(
                "Café",
                List.of(firstVariant(), secondVariant()),
                "{\"items\":[1,2]}",
                List.of("https://images.example.test/one"));
        byte[] replacement = canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, base));
        PublicationCandidate reversedDimensions = new PublicationCandidate(
                base.content(), base.category(), new OptionDimensions(List.of("Color", "Size")), base.variants());
        PublicationCandidate reversedOptionValues = new PublicationCandidate(
                base.content(),
                base.category(),
                base.optionDimensions(),
                List.of(
                        new ConfiguredVariant(
                                FIRST_VARIANT,
                                new OptionValues(List.of("Blue", "Small")),
                                true,
                                InrMoney.parse("10", "INR")),
                        secondVariant()));
        PublicationCandidate changedSellability = new PublicationCandidate(
                base.content(),
                base.category(),
                base.optionDimensions(),
                List.of(
                        new ConfiguredVariant(
                                FIRST_VARIANT,
                                new OptionValues(List.of("Small", "Blue")),
                                false,
                                InrMoney.parse("10", "INR")),
                        secondVariant()));
        PublicationCandidate changedPrice = new PublicationCandidate(
                base.content(),
                base.category(),
                base.optionDimensions(),
                List.of(
                        new ConfiguredVariant(
                                FIRST_VARIANT,
                                new OptionValues(List.of("Small", "Blue")),
                                true,
                                InrMoney.parse("10.01", "INR")),
                        secondVariant()));

        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, reversedDimensions)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, reversedOptionValues)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, changedSellability)))
                .isNotEqualTo(replacement);
        assertThat(canonicalizer.canonicalize(new ReplaceCandidateMutation(PRODUCT_ID, 4, changedPrice)))
                .isNotEqualTo(replacement);

        byte[] firstVariantTarget = canonicalizer.canonicalize(
                new LifecycleMutation(ProductLifecycleOperation.WITHDRAW_VARIANT, PRODUCT_ID, FIRST_VARIANT, 4));
        assertThat(canonicalizer.canonicalize(new LifecycleMutation(
                        ProductLifecycleOperation.WITHDRAW_VARIANT, PRODUCT_ID, SECOND_VARIANT, 4)))
                .isNotEqualTo(firstVariantTarget);
        assertThat(canonicalizer.canonicalize(
                        new LifecycleMutation(ProductLifecycleOperation.REPUBLISH, PRODUCT_ID, null, 4)))
                .isNotEqualTo(canonicalizer.canonicalize(
                        new LifecycleMutation(ProductLifecycleOperation.PUBLISH, PRODUCT_ID, null, 4)));
    }

    private static PublicationCandidate candidate(
            String name, List<ConfiguredVariant> variants, String longDescription, List<String> imageUrls) {
        return new PublicationCandidate(
                new ProductContent(
                        name, "Description", new ImageUrls(imageUrls), new LongDescriptionDocument(longDescription)),
                new CategorySelection(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001")), null),
                new OptionDimensions(List.of("Size", "Color")),
                variants);
    }

    private static ConfiguredVariant firstVariant() {
        return new ConfiguredVariant(
                FIRST_VARIANT, new OptionValues(List.of("Small", "Blue")), true, InrMoney.parse("10", "INR"));
    }

    private static ConfiguredVariant secondVariant() {
        return new ConfiguredVariant(SECOND_VARIANT, new OptionValues(List.of("Large", "Red")), false, null);
    }
}
