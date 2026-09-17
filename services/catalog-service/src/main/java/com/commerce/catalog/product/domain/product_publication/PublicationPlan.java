package com.commerce.catalog.product.domain.product_publication;

import com.commerce.catalog.product.domain.InrMoney;
import java.util.Objects;

public record PublicationPlan(
        PublicationCandidate candidate, InrMoney representativePrice, boolean priceFrom, int sellableCount) {
    public PublicationPlan {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(representativePrice, "representativePrice");
        if (sellableCount < 1 || sellableCount > PublicationCandidate.MAX_VARIANTS) {
            throw new IllegalArgumentException("sellableCount must be between 1 and 100");
        }
    }
}
