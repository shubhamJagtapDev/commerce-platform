package com.commerce.catalog.product.domain.policies;

import com.commerce.catalog.product.domain.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.category.CategoryTaxonomy;
import com.commerce.catalog.product.domain.product_publication.*;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import org.jspecify.annotations.Nullable;

public final class PublicationPolicy {
    public static final int MAX_VIOLATIONS = 20;

    public PublicationValidationResult validate(
            PublicationCandidate candidate,
            CategoryTaxonomy taxonomy,
            List<PublishedVariantMeaning> previouslySellableMeanings) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(taxonomy, "taxonomy");
        Objects.requireNonNull(previouslySellableMeanings, "previouslySellableMeanings");

        List<PublicationViolation> violations = new ArrayList<>();
        validateContent(candidate, violations);
        validateCategory(candidate.category(), taxonomy, violations);
        validateVariants(candidate, previousMeaningsByVariant(previouslySellableMeanings), violations);

        if (!violations.isEmpty()) {
            return PublicationValidationResult.rejected(violations);
        }

        return PublicationValidationResult.accepted(derivePlan(candidate));
    }

    private void validateContent(PublicationCandidate candidate, List<PublicationViolation> violations) {
        if (candidate.content().name() == null) {
            add(violations, "name", PublicationViolationCode.REQUIRED_NAME);
        }
        if (candidate.content().shortDescription() == null) {
            add(violations, "shortDescription", PublicationViolationCode.REQUIRED_SHORT_DESCRIPTION);
        }
    }

    private void validateCategory(
        CategorySelection selection, CategoryTaxonomy taxonomy, List<PublicationViolation> violations) {
        if (!taxonomy.containsMain(selection.mainCategoryId())) {
            add(violations, "mainCategoryId", PublicationViolationCode.UNKNOWN_MAIN_CATEGORY);
            return;
        }
        if (selection.subcategoryId() != null
                && !taxonomy.containsSubcategory(selection.mainCategoryId(), selection.subcategoryId())) {
            add(violations, "subcategoryId", PublicationViolationCode.INVALID_SUBCATEGORY);
        }
    }

    private void validateVariants(
            PublicationCandidate candidate,
            Map<VariantId, PublishedVariantMeaning> previouslySellableMeanings,
            List<PublicationViolation> violations) {
        int optionCount = candidate.optionDimensions().names().size();
        if (optionCount == 0 && candidate.variants().size() != 1) {
            add(violations, "variants", PublicationViolationCode.DEFAULT_VARIANT_COUNT_INVALID);
        }

        Set<List<String>> selections = new HashSet<>();
        int sellableCount = 0;
        for (int index = 0; index < candidate.variants().size(); index++) {
            ConfiguredVariant variant = candidate.variants().get(index);
            String field = "variants[" + index + "]";
            if (variant.optionValues().values().size() != optionCount) {
                add(violations, field + ".optionValues", PublicationViolationCode.OPTION_SELECTION_ARITY_MISMATCH);
            }
            if (!selections.add(variant.optionValues().values())) {
                add(violations, field + ".optionValues", PublicationViolationCode.DUPLICATE_OPTION_SELECTION);
            }
            if (variant.sellable()) {
                sellableCount++;
                if (variant.price() == null) {
                    add(violations, field + ".price", PublicationViolationCode.SELLABLE_PRICE_REQUIRED);
                }
            }
            validateStableMeaning(
                    candidate.optionDimensions(),
                    variant,
                    previouslySellableMeanings.get(variant.variantId()),
                    field,
                    violations);
        }

        if (sellableCount == 0) {
            add(violations, "variants", PublicationViolationCode.NO_SELLABLE_VARIANT);
        }
    }

    private void validateStableMeaning(
            OptionDimensions candidateDimensions,
            ConfiguredVariant candidateVariant,
            @Nullable PublishedVariantMeaning publishedMeaning,
            String field,
            List<PublicationViolation> violations) {
        if (publishedMeaning == null) {
            return;
        }
        if (!publishedMeaning.optionDimensions().equals(candidateDimensions)
                || !publishedMeaning.optionValues().equals(candidateVariant.optionValues())) {
            add(violations, field + ".variantId", PublicationViolationCode.STABLE_VARIANT_MEANING_CHANGED);
        }
    }

    private PublicationPlan derivePlan(PublicationCandidate candidate) {
        @Nullable InrMoney minimum = null;
        @Nullable InrMoney maximum = null;
        int sellableCount = 0;
        for (ConfiguredVariant variant : candidate.variants()) {
            if (!variant.sellable()) {
                continue;
            }

            InrMoney price = Objects.requireNonNull(variant.price(), "validated sellable price");
            sellableCount++;
            minimum = minimum == null || price.compareTo(minimum) < 0 ? price : minimum;
            maximum = maximum == null || price.compareTo(maximum) > 0 ? price : maximum;
        }

        return new PublicationPlan(
                candidate,
                Objects.requireNonNull(minimum, "validated representative price"),
                !Objects.requireNonNull(minimum, "validated minimum")
                        .equals(Objects.requireNonNull(maximum, "validated maximum")),
                sellableCount);
    }

    private Map<VariantId, PublishedVariantMeaning> previousMeaningsByVariant(
            List<PublishedVariantMeaning> previouslySellableMeanings) {
        Map<VariantId, PublishedVariantMeaning> meanings = new HashMap<>();
        for (PublishedVariantMeaning meaning : previouslySellableMeanings) {
            meanings.put(meaning.variantId(), meaning);
        }
        return meanings;
    }

    private void add(List<PublicationViolation> violations, String field, PublicationViolationCode code) {
        if (violations.size() < MAX_VIOLATIONS) {
            violations.add(new PublicationViolation(field, code));
        }
    }
}
