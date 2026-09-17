package com.commerce.catalog.product.domain.product_models;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ImageUrls(List<String> values) {
    public static final int MAX_IMAGES = 10;
    private static final int MAX_URL_LENGTH = 2_048;

    public ImageUrls {
        Objects.requireNonNull(values, "values");
        if (values.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("imageUrls must contain at most " + MAX_IMAGES + " values");
        }

        List<String> normalized = new ArrayList<>(values.size());
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            String trimmed = Objects.requireNonNull(value, "image URL").trim();
            validateUrl(trimmed);
            if (!seen.add(trimmed)) {
                throw new IllegalArgumentException("imageUrls must not contain duplicates");
            }
            normalized.add(trimmed);
        }
        values = List.copyOf(normalized);
    }

    public static ImageUrls none() {
        return new ImageUrls(List.of());
    }

    private static void validateUrl(String value) {
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("image URL length is invalid");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("image URL must not contain control characters");
        }

        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("image URL must be absolute HTTPS with a host");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("image URL must not contain user info or a fragment");
        }
    }
}
