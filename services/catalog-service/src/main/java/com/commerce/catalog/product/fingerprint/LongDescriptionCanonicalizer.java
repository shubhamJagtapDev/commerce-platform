package com.commerce.catalog.product.fingerprint;

import com.commerce.catalog.product.domain.product_models.LongDescriptionDocument;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public final class LongDescriptionCanonicalizer {
    private static final int MAX_NUMBER_TOKEN_LENGTH = 128;
    private static final int MAX_EXPONENT_MAGNITUDE = 1_000;
    private static final int MAX_STRING_LENGTH = LongDescriptionDocument.MAX_BYTES;

    private final JsonFactory jsonFactory = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxDocumentLength(LongDescriptionDocument.MAX_BYTES)
                    .maxNestingDepth(LongDescriptionDocument.MAX_DEPTH)
                    .maxNumberLength(MAX_NUMBER_TOKEN_LENGTH)
                    .maxStringLength(MAX_STRING_LENGTH)
                    .maxNameLength(MAX_STRING_LENGTH)
                    .build())
            .build();

    public byte[] canonicalize(LongDescriptionDocument document) {
        Objects.requireNonNull(document, "document");
        return canonicalize(document.jsonObject());
    }

    public byte[] canonicalize(String jsonObject) {
        Objects.requireNonNull(jsonObject, "jsonObject");
        validateUnicode(jsonObject);
        byte[] input = jsonObject.getBytes(StandardCharsets.UTF_8);
        if (input.length > LongDescriptionDocument.MAX_BYTES) {
            throw invalidJson();
        }

        try (JsonParser parser = jsonFactory.createParser(input)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_OBJECT) {
                throw invalidJson();
            }
            CanonicalValue value = readValue(parser, firstToken);
            if (parser.nextToken() != null) {
                throw invalidJson();
            }
            return write(value);
        } catch (IOException | ArithmeticException ignored) {
            throw invalidJson();
        }
    }

    private CanonicalValue readValue(JsonParser parser, JsonToken token) throws IOException {
        return switch (token) {
            case START_OBJECT -> readObject(parser);
            case START_ARRAY -> readArray(parser);
            case VALUE_STRING -> {
                String value = parser.getText();
                validateUnicode(value);
                yield new StringValue(value);
            }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new NumberValue(canonicalNumber(parser.getText()));
            case VALUE_TRUE -> BooleanValue.TRUE;
            case VALUE_FALSE -> BooleanValue.FALSE;
            case VALUE_NULL -> NullValue.INSTANCE;
            default -> throw invalidJson();
        };
    }

    private ObjectValue readObject(JsonParser parser) throws IOException {
        Map<String, CanonicalValue> fields = new TreeMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw invalidJson();
            }
            String fieldName = parser.currentName();
            validateUnicode(fieldName);
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null || fields.put(fieldName, readValue(parser, valueToken)) != null) {
                throw invalidJson();
            }
        }
        return new ObjectValue(fields);
    }

    private ArrayValue readArray(JsonParser parser) throws IOException {
        List<CanonicalValue> values = new ArrayList<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw invalidJson();
            }
            values.add(readValue(parser, token));
        }
        return new ArrayValue(values);
    }

    private static String canonicalNumber(String token) {
        if (token.length() > MAX_NUMBER_TOKEN_LENGTH) {
            throw invalidJson();
        }
        int exponentMarker = Math.max(token.indexOf('e'), token.indexOf('E'));
        if (exponentMarker >= 0) {
            int exponent;
            try {
                exponent = Integer.parseInt(token.substring(exponentMarker + 1));
            } catch (NumberFormatException ignored) {
                throw invalidJson();
            }
            if (Math.abs((long) exponent) > MAX_EXPONENT_MAGNITUDE) {
                throw invalidJson();
            }
        }

        BigDecimal number = new BigDecimal(token);
        if (number.signum() == 0) {
            return "0";
        }
        return number.stripTrailingZeros().toPlainString();
    }

    private byte[] write(CanonicalValue value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
            writeValue(generator, value);
        }
        return output.toByteArray();
    }

    private void writeValue(JsonGenerator generator, CanonicalValue value) throws IOException {
        switch (value) {
            case ObjectValue object -> {
                generator.writeStartObject();
                for (Map.Entry<String, CanonicalValue> field : object.fields().entrySet()) {
                    generator.writeFieldName(field.getKey());
                    writeValue(generator, field.getValue());
                }
                generator.writeEndObject();
            }
            case ArrayValue array -> {
                generator.writeStartArray();
                for (CanonicalValue element : array.values()) {
                    writeValue(generator, element);
                }
                generator.writeEndArray();
            }
            case StringValue string -> generator.writeString(string.value());
            case NumberValue number -> generator.writeNumber(number.value());
            case BooleanValue bool -> generator.writeBoolean(bool.value());
            case NullValue ignored -> generator.writeNull();
        }
    }

    private static void validateUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\0') {
                throw invalidJson();
            }
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw invalidJson();
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw invalidJson();
            }
        }
    }

    private static IllegalArgumentException invalidJson() {
        return new IllegalArgumentException("longDescription is not valid canonicalizable JSON");
    }

    private sealed interface CanonicalValue
            permits ArrayValue, BooleanValue, NullValue, NumberValue, ObjectValue, StringValue {}

    private record ObjectValue(Map<String, CanonicalValue> fields) implements CanonicalValue {
        private ObjectValue {
            fields = Collections.unmodifiableMap(new TreeMap<>(fields));
        }
    }

    private record ArrayValue(List<CanonicalValue> values) implements CanonicalValue {
        private ArrayValue {
            values = List.copyOf(values);
        }
    }

    private record StringValue(String value) implements CanonicalValue {}

    private record NumberValue(String value) implements CanonicalValue {}

    private enum BooleanValue implements CanonicalValue {
        TRUE(true),
        FALSE(false);

        private final boolean value;

        BooleanValue(boolean value) {
            this.value = value;
        }

        boolean value() {
            return value;
        }
    }

    private enum NullValue implements CanonicalValue {
        INSTANCE
    }
}
