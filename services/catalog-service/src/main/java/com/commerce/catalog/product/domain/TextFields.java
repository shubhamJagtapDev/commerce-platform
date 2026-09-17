package com.commerce.catalog.product.domain;

import java.text.Normalizer;
import org.jspecify.annotations.Nullable;

public final class TextFields {
    private TextFields() {}

    public static @Nullable String normalizeOptional(String field, @Nullable String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }

        return normalizeRequired(field, value, maxCodePoints);
    }

    public static String normalizeRequired(String field, String value, int maxCodePoints) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(field + " exceeds " + maxCodePoints + " code points");
        }
        if (containsControlCharacter(normalized)) {
            throw new IllegalArgumentException(field + " must not contain control characters");
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
