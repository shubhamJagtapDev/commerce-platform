package com.commerce.catalog.product.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

final class SetFactory {
    private SetFactory() {}

    @SafeVarargs
    static <T> Set<T> of(T... values) {
        return Arrays.stream(values).collect(Collectors.toUnmodifiableSet());
    }
}
