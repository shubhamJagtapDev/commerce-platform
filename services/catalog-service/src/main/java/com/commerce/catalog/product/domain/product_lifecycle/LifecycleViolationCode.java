package com.commerce.catalog.product.domain.product_lifecycle;

public enum LifecycleViolationCode {
    INVALID_STATE,
    NEVER_PUBLISHED_REQUIRED,
    TARGET_VARIANT_NOT_SELLABLE,
    INVALID_SELLABLE_COUNT
}
