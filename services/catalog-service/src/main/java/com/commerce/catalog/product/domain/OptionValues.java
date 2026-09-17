package com.commerce.catalog.product.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record OptionValues(List<String> values) {
    public static final int MAX_VALUES = 2;
    public static final int MAX_VALUE_CODE_POINTS = 50;

    public OptionValues {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_VALUES) {
            throw new IllegalArgumentException("optionValues must contain at most " + MAX_VALUES + " values");
        }

        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(TextFields.normalizeRequired("optionValues", value, MAX_VALUE_CODE_POINTS));
        }
        values = List.copyOf(normalized);
    }

    public static OptionValues none() {
        return new OptionValues(List.of());
    }
}
