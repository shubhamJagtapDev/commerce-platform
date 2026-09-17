package com.commerce.catalog.product.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LongDescriptionCanonicalizerTest {
    private final LongDescriptionCanonicalizer canonicalizer = new LongDescriptionCanonicalizer();

    @Test
    void equivalentJsonSpellingsHaveOneFrozenCanonicalForm() {
        String escapedA = Character.toString('\\') + "u0061";
        String first = """
                {"nested":{"z":"é","a":-0.0},"n":1.2300e0,"a":[3,2,1],"escaped":"%s"}
                """.formatted(escapedA);
        String second = """
                { "escaped" : "a", "a" : [3,2,1], "n" : 1.23, "nested" : {"a":0,"z":"é"} }
                """;

        byte[] firstCanonical = canonicalizer.canonicalize(first);
        byte[] secondCanonical = canonicalizer.canonicalize(second);

        assertThat(firstCanonical).isEqualTo(secondCanonical);
        assertThat(new String(firstCanonical, StandardCharsets.UTF_8))
                .isEqualTo("{\"a\":[3,2,1],\"escaped\":\"a\",\"n\":1.23,\"nested\":{\"a\":0,\"z\":\"é\"}}");
    }

    @Test
    void arrayAndStringValuesRemainSemanticallyOpaque() {
        assertThat(canonicalizer.canonicalize("{\"a\":[1,2],\"s\":\" Value \"}"))
                .isNotEqualTo(canonicalizer.canonicalize("{\"a\":[2,1],\"s\":\" Value \"}"));
        assertThat(canonicalizer.canonicalize("{\"s\":\"Value\"}"))
                .isNotEqualTo(canonicalizer.canonicalize("{\"s\":\" Value \"}"));
    }

    @Test
    void objectNamesUseJavaUnicodeCodeUnitOrder() {
        String supplementary = new String(Character.toChars(0x10000));
        String privateUse = "\uE000";
        String escapedSupplementary = Character.toString('\\') + "uD800" + Character.toString('\\') + "uDC00";

        String canonical = new String(
                canonicalizer.canonicalize("{\"" + privateUse + "\":1,\"" + supplementary + "\":2}"),
                StandardCharsets.UTF_8);

        assertThat(canonical).isEqualTo("{\"" + escapedSupplementary + "\":2,\"" + privateUse + "\":1}");
    }

    @Test
    void approvedDepthSizeNumberAndExponentBoundariesAreAccepted() {
        String largestDocument = "{\"a\":\"" + "x".repeat(32 * 1024 - 8) + "\"}";
        String boundaryNumbers = "{\"large\":" + "9".repeat(128) + ",\"high\":1e1000,\"low\":1e-1000}";

        assertThat(largestDocument.getBytes(StandardCharsets.UTF_8)).hasSize(32 * 1024);
        assertThat(canonicalizer.canonicalize(largestDocument)).isNotEmpty();
        assertThat(canonicalizer.canonicalize(nestedObject(10))).isNotEmpty();
        assertThat(canonicalizer.canonicalize(boundaryNumbers)).isNotEmpty();
    }

    @Test
    void malformedAndOutOfBoundsJsonIsRejectedWithoutEchoingContent() {
        assertRejected("[]");
        assertRejected("{\"duplicate\":1,\"duplicate\":2}");
        assertRejected("{\"unterminated\":");
        assertRejected("{\"nul\":\"\\u0000\"}");
        assertRejected("{\"\\u0000\":true}");
        assertRejected("{\"surrogate\":\"\\uD800\"}");
        assertRejected("{\"\\uD800\":true}");
        assertRejected("{\"number\":" + "1".repeat(129) + "}");
        assertRejected("{\"number\":1e1001}");
        assertRejected("{\"number\":1e-1001}");
        assertRejected(nestedObject(11));
        assertRejected("{\"large\":\"" + "x".repeat(32 * 1024) + "\"}");
    }

    private void assertRejected(String json) {
        assertThatThrownBy(() -> canonicalizer.canonicalize(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longDescription is not valid canonicalizable JSON")
                .hasMessageNotContaining(json);
    }

    private static String nestedObject(int depth) {
        return "{\"a\":".repeat(depth) + "0" + "}".repeat(depth);
    }
}
