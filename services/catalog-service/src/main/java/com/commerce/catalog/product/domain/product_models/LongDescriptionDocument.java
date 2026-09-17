package com.commerce.catalog.product.domain.product_models;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record LongDescriptionDocument(String jsonObject) {
    public static final int MAX_BYTES = 32 * 1024;
    public static final int MAX_DEPTH = 10;

    public LongDescriptionDocument {
        Objects.requireNonNull(jsonObject, "jsonObject");
        if (jsonObject.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("longDescription exceeds " + MAX_BYTES + " UTF-8 bytes");
        }
        validateObjectShape(jsonObject);
    }

    private static void validateObjectShape(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("longDescription must be a JSON object");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\0') {
                throw new IllegalArgumentException("longDescription must not contain NUL");
            }
            if (Character.isHighSurrogate(current)
                    && (index + 1 == value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))) {
                throw new IllegalArgumentException("longDescription contains an unpaired surrogate");
            }
            if (Character.isLowSurrogate(current)
                    && (index == 0 || !Character.isHighSurrogate(value.charAt(index - 1)))) {
                throw new IllegalArgumentException("longDescription contains an unpaired surrogate");
            }

            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{' || current == '[') {
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new IllegalArgumentException("longDescription exceeds JSON depth " + MAX_DEPTH);
                }
            } else if (current == '}' || current == ']') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("longDescription JSON delimiters are invalid");
                }
            }
        }
        if (inString || escaping || depth != 0) {
            throw new IllegalArgumentException("longDescription JSON is incomplete");
        }
    }
}
