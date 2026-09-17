package com.commerce.catalog.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

final class CatalogContract {
    static final String PREFIX = "/api/v1/catalog/maintenance";
    static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());
    private static final Set<String> DOCUMENTATION =
            Set.of("description", "summary", "example", "examples", "title", "tags", "externalDocs", "deprecated");

    private CatalogContract() {}

    static JsonNode read() throws IOException {
        return YAML.readTree(Path.of(System.getProperty("catalog.contract")).toFile());
    }

    static JsonNode baseline() throws IOException {
        try (var stream = CatalogContract.class.getResourceAsStream("/contracts/catalog-probe-baseline.yaml")) {
            return YAML.readTree(stream);
        }
    }

    static JsonNode resolve(JsonNode document, JsonNode node) {
        if (!node.has("$ref")) {
            return node;
        }
        String reference = node.path("$ref").asText();
        assertThat(reference).startsWith("#/components/");
        JsonNode resolved = document.at(reference.substring(1));
        assertThat(resolved.isMissingNode())
                .as("Unresolved reference %s", reference)
                .isFalse();
        return resolve(document, resolved);
    }

    // Compare wire semantics after expanding local references, independently of component names
    // and explanatory text. This contract has no recursive schemas.
    static JsonNode wireShape(JsonNode document, JsonNode node) {
        return wireShape(document, node, false);
    }

    private static JsonNode wireShape(JsonNode document, JsonNode node, boolean namedEntries) {
        JsonNode resolved = resolve(document, node);
        if (resolved.isArray()) {
            ArrayNode result = YAML.createArrayNode();
            for (JsonNode child : resolved) {
                result.add(wireShape(document, child));
            }
            return result;
        }
        if (!resolved.isObject()) {
            return resolved;
        }
        ObjectNode result = YAML.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = resolved.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String key = field.getKey();
            if (namedEntries || (!DOCUMENTATION.contains(key) && !key.startsWith("x-"))) {
                boolean childrenAreNames = Set.of("properties", "headers", "content", "responses", "securitySchemes")
                        .contains(key);
                JsonNode value = wireShape(document, field.getValue(), childrenAreNames);
                if (!namedEntries && Set.of("required", "enum", "type").contains(key) && value.isArray()) {
                    var entries = new java.util.ArrayList<JsonNode>();
                    value.forEach(entries::add);
                    entries.sort(java.util.Comparator.comparing(JsonNode::toString));
                    ArrayNode sorted = YAML.createArrayNode();
                    entries.forEach(sorted::add);
                    value = sorted;
                }
                result.set(key, value);
            }
        }
        return result;
    }
}
