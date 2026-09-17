package com.commerce.catalog.product.domain.product_publication;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PublicationValidationResult(
        List<PublicationViolation> violations, @Nullable PublicationPlan plan) {
    public PublicationValidationResult {
        violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        if (violations.isEmpty() == (plan == null)) {
            throw new IllegalArgumentException("exactly one of violations or plan must be present");
        }
    }

    public static PublicationValidationResult accepted(PublicationPlan plan) {
        return new PublicationValidationResult(List.of(), plan);
    }

    public static PublicationValidationResult rejected(List<PublicationViolation> violations) {
        return new PublicationValidationResult(violations, null);
    }

    public boolean accepted() {
        return violations.isEmpty();
    }
}
