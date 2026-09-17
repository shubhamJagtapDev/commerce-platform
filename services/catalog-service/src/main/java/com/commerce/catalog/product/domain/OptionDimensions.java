package com.commerce.catalog.product.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record OptionDimensions(List<String> names) {
    public static final int MAX_DIMENSIONS = 2;
    public static final int MAX_NAME_CODE_POINTS = 50;

    public OptionDimensions {
        Objects.requireNonNull(names, "names");
        if (names.size() > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("optionDimensions must contain at most " + MAX_DIMENSIONS + " values");
        }

        List<String> normalized = new ArrayList<>(names.size());
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String normalizedName = TextFields.normalizeRequired("optionDimensions", name, MAX_NAME_CODE_POINTS);
            if (!seen.add(normalizedName)) {
                throw new IllegalArgumentException("optionDimensions must be distinct");
            }
            normalized.add(normalizedName);
        }
        names = List.copyOf(normalized);
    }

    public static OptionDimensions none() {
        return new OptionDimensions(List.of());
    }
}
