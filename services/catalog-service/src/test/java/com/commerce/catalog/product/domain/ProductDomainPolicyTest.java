package com.commerce.catalog.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.category.CategoryTaxonomy;
import com.commerce.catalog.product.domain.policies.ProductLifecyclePolicy;
import com.commerce.catalog.product.domain.policies.PublicationPolicy;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import com.commerce.catalog.product.domain.product_lifecycle.LifecycleViolationCode;
import com.commerce.catalog.product.domain.product_models.*;
import com.commerce.catalog.product.domain.product_publication.*;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import org.junit.jupiter.api.Test;

class ProductDomainPolicyTest {
    private static final CategoryId MAIN_CATEGORY_ID =
            new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final CategoryId SUBCATEGORY_ID =
            new CategoryId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final CategoryTaxonomy TAXONOMY =
            new CategoryTaxonomy(SetFactory.of(MAIN_CATEGORY_ID), Map.of(SUBCATEGORY_ID, MAIN_CATEGORY_ID));

    private final PublicationPolicy publicationPolicy = new PublicationPolicy();
    private final ProductLifecyclePolicy lifecyclePolicy = new ProductLifecyclePolicy();

    @Test
    void inrMoneyRejectsValuesThatWouldNeedRoundingOrCurrencyRepair() {
        assertThat(InrMoney.parse("1299", "INR").responseAmount()).isEqualTo("1299.00");
        assertThat(InrMoney.parse("1299.5", "INR").responseAmount()).isEqualTo("1299.50");

        assertThatThrownBy(() -> InrMoney.parse("10.001", "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InrMoney.parse("0.00", "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InrMoney.parse(" 10.00", "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InrMoney.parse("10.00", "USD")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentTextIsNormalizedAndRejectsControlCharacters() {
        ProductContent content = new ProductContent(" Cafe\u0301 ", " short ", ImageUrls.none(), null);

        assertThat(content.name()).isEqualTo("Caf\u00e9");
        assertThat(content.shortDescription()).isEqualTo("short");
        assertThatThrownBy(() -> new ProductContent("bad\nname", "short", ImageUrls.none(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void imageUrlsAreBoundedHttpsOnlyAndPreserveOrder() {
        ImageUrls urls = new ImageUrls(List.of(
                " https://images.example.test/products/1.png?size=large ",
                "https://images.example.test/products/2.png"));

        assertThat(urls.values())
                .containsExactly(
                        "https://images.example.test/products/1.png?size=large",
                        "https://images.example.test/products/2.png");
        assertThatThrownBy(() -> new ImageUrls(List.of("http://images.example.test/a.png")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageUrls(List.of("https://user@example.test/a.png")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageUrls(List.of("https://images.example.test/a.png#frag")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageUrls(
                        List.of("https://images.example.test/a.png", " https://images.example.test/a.png ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void longDescriptionRejectsNonObjectOversizeAndExcessiveDepth() {
        assertThat(new LongDescriptionDocument("{\"details\":{\"material\":\"cotton\"}}").jsonObject())
                .contains("material");

        assertThatThrownBy(() -> new LongDescriptionDocument("[]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LongDescriptionDocument(
                        "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":{\"f\":{\"g\":{\"h\":{\"i\":{\"j\":{}}}}}}}}}}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completeCandidateDerivesRepresentativePriceAndPriceFromDeterministically() {
        PublicationValidationResult result = publicationPolicy.validate(completeCandidate(), TAXONOMY, List.of());
        PublicationPlan plan = Objects.requireNonNull(result.plan());

        assertThat(result.accepted()).isTrue();
        assertThat(plan.representativePrice().responseAmount()).isEqualTo("999.00");
        assertThat(plan.priceFrom()).isTrue();
        assertThat(plan.sellableCount()).isEqualTo(2);
    }

    @Test
    void publicationWithoutImagesAndSubcategoryIsCompleteWhenRequiredFieldsAndVariantArePresent() {
        PublicationCandidate candidate = new PublicationCandidate(
                new ProductContent("Gift card", "Digital delivery", ImageUrls.none(), null),
                new CategorySelection(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000021")), null),
                OptionDimensions.none(),
                List.of(new ConfiguredVariant(
                        new VariantId(UUID.randomUUID()), OptionValues.none(), true, InrMoney.parse("500", "INR"))));
        CategoryTaxonomy mainOnlyTaxonomy = new CategoryTaxonomy(
                SetFactory.of(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000021"))), Map.of());

        PublicationValidationResult result = publicationPolicy.validate(candidate, mainOnlyTaxonomy, List.of());
        PublicationPlan plan = Objects.requireNonNull(result.plan());

        assertThat(result.accepted()).isTrue();
        assertThat(plan.priceFrom()).isFalse();
    }

    @Test
    void publicationPolicyReturnsBoundedStructuredViolationsWithoutRepairingCandidate() {
        PublicationCandidate candidate = new PublicationCandidate(
                new ProductContent(null, null, ImageUrls.none(), null),
                new CategorySelection(MAIN_CATEGORY_ID, SUBCATEGORY_ID),
                new OptionDimensions(List.of("Size")),
                IntStream.range(0, PublicationCandidate.MAX_VARIANTS)
                        .mapToObj(index -> new ConfiguredVariant(
                                new VariantId(new UUID(0L, index + 1L)), OptionValues.none(), true, null))
                        .toList());
        CategoryTaxonomy emptyTaxonomy = new CategoryTaxonomy(SetFactory.of(), Map.of());

        PublicationValidationResult result = publicationPolicy.validate(candidate, emptyTaxonomy, List.of());

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations()).hasSize(PublicationPolicy.MAX_VIOLATIONS);
        assertThat(result.violations())
                .extracting(PublicationViolation::code)
                .contains(
                        PublicationViolationCode.REQUIRED_NAME,
                        PublicationViolationCode.REQUIRED_SHORT_DESCRIPTION,
                        PublicationViolationCode.UNKNOWN_MAIN_CATEGORY,
                        PublicationViolationCode.OPTION_SELECTION_ARITY_MISMATCH,
                        PublicationViolationCode.SELLABLE_PRICE_REQUIRED);
    }

    @Test
    void previouslySellableVariantMeaningCannotChange() {
        VariantId variantId = new VariantId(UUID.randomUUID());
        PublicationCandidate candidate = new PublicationCandidate(
                completeCandidate().content(),
                completeCandidate().category(),
                new OptionDimensions(List.of("Color")),
                List.of(new ConfiguredVariant(
                        variantId, new OptionValues(List.of("Blue")), true, InrMoney.parse("100", "INR"))));

        PublicationValidationResult result = publicationPolicy.validate(
                candidate,
                TAXONOMY,
                List.of(new PublishedVariantMeaning(
                        variantId, new OptionDimensions(List.of("Color")), new OptionValues(List.of("Red")))));

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(PublicationViolation::code)
                .contains(PublicationViolationCode.STABLE_VARIANT_MEANING_CHANGED);
    }

    @Test
    void noOptionCandidateAllowsOnlyOneDefaultVariant() {
        PublicationCandidate candidate = new PublicationCandidate(
                completeCandidate().content(),
                completeCandidate().category(),
                OptionDimensions.none(),
                List.of(
                        new ConfiguredVariant(
                                new VariantId(UUID.randomUUID()),
                                OptionValues.none(),
                                true,
                                InrMoney.parse("100", "INR")),
                        new ConfiguredVariant(
                                new VariantId(UUID.randomUUID()),
                                OptionValues.none(),
                                true,
                                InrMoney.parse("200", "INR"))));

        PublicationValidationResult result = publicationPolicy.validate(candidate, TAXONOMY, List.of());

        assertThat(result.accepted()).isFalse();
        assertThat(result.violations())
                .extracting(PublicationViolation::code)
                .contains(PublicationViolationCode.DEFAULT_VARIANT_COUNT_INVALID);
    }

    @Test
    void lifecyclePolicyAcceptsOnlyApprovedStateTransitions() {
        assertThat(lifecyclePolicy.createDraft().resultState()).isEqualTo(ProductCommandResultState.DRAFT);
        assertThat(lifecyclePolicy.publish(ProductState.DRAFT).resultState())
                .isEqualTo(ProductCommandResultState.PUBLISHED);
        assertThat(lifecyclePolicy.republish(ProductState.PUBLISHED).accepted()).isTrue();
        assertThat(lifecyclePolicy.withdrawProduct(ProductState.PUBLISHED).resultState())
                .isEqualTo(ProductCommandResultState.WITHDRAWN);
        assertThat(lifecyclePolicy
                        .withdrawVariant(ProductState.PUBLISHED, 1, true)
                        .resultState())
                .isEqualTo(ProductCommandResultState.WITHDRAWN);
        assertThat(lifecyclePolicy
                        .withdrawVariant(ProductState.PUBLISHED, 2, true)
                        .resultState())
                .isEqualTo(ProductCommandResultState.PUBLISHED);
        assertThat(lifecyclePolicy.reactivate(ProductState.WITHDRAWN).resultState())
                .isEqualTo(ProductCommandResultState.PUBLISHED);
        assertThat(lifecyclePolicy.discard(ProductState.DRAFT, false).resultState())
                .isEqualTo(ProductCommandResultState.DISCARDED);

        assertThat(lifecyclePolicy.publish(ProductState.PUBLISHED).violationCode())
                .isEqualTo(LifecycleViolationCode.INVALID_STATE);
        assertThat(lifecyclePolicy.discard(ProductState.DRAFT, true).violationCode())
                .isEqualTo(LifecycleViolationCode.NEVER_PUBLISHED_REQUIRED);
        assertThat(lifecyclePolicy
                        .withdrawVariant(ProductState.PUBLISHED, 2, false)
                        .violationCode())
                .isEqualTo(LifecycleViolationCode.TARGET_VARIANT_NOT_SELLABLE);
    }

    private PublicationCandidate completeCandidate() {
        return new PublicationCandidate(
                new ProductContent(
                        "Cotton shirt",
                        "Soft daily shirt",
                        new ImageUrls(List.of("https://images.example.test/products/shirt-front.jpg")),
                        new LongDescriptionDocument("{\"care\":\"cold wash\"}")),
                new CategorySelection(MAIN_CATEGORY_ID, SUBCATEGORY_ID),
                new OptionDimensions(List.of("Size")),
                List.of(
                        new ConfiguredVariant(
                                new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                                new OptionValues(List.of("M")),
                                true,
                                InrMoney.parse("1299.00", "INR")),
                        new ConfiguredVariant(
                                new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                                new OptionValues(List.of("L")),
                                true,
                                InrMoney.parse("999.00", "INR")),
                        new ConfiguredVariant(
                                new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
                                new OptionValues(List.of("XL")),
                                false,
                                null)));
    }
}
