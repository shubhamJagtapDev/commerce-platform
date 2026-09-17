package com.commerce.catalog.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CatalogOpenApiContractTest {
    private static final String P = CatalogContract.PREFIX + "/products";
    private static final List<Operation> OPERATIONS = List.of(
            new Operation("post", P, "createCatalogDraft", "CreateDraftRequest", "201", "CommandOutcome"),
            new Operation("get", P, "listMaintainerProducts", "", "200", "ProductPage"),
            new Operation("get", P + "/{productId}", "getMaintainerProduct", "", "200", "MaintainerProduct"),
            new Operation(
                    "put",
                    P + "/{productId}/candidate",
                    "replaceCatalogCandidate",
                    "ReplaceCandidateRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/publish",
                    "publishCatalogProduct",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/republish",
                    "republishCatalogProduct",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/withdraw",
                    "withdrawCatalogProduct",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/reactivate",
                    "reactivateCatalogProduct",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/variants/{variantId}/withdraw",
                    "withdrawCatalogVariant",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "post",
                    P + "/{productId}/discard",
                    "discardCatalogDraft",
                    "LifecycleRequest",
                    "200",
                    "CommandOutcome"),
            new Operation(
                    "get",
                    CatalogContract.PREFIX + "/categories",
                    "listMaintainerCategories",
                    "",
                    "200",
                    "CategoryPage"));

    @Test
    void approvedOperationsHaveBoundedAuthenticatedRequestsAndStableOutcomes() throws IOException {
        JsonNode document = CatalogContract.read();
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        Set<String> actual = new HashSet<>();
        Set<String> ids = new HashSet<>();
        document.path("paths")
                .fields()
                .forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
                    if (CatalogContract.METHODS.contains(method.getKey())) {
                        assertThat(ids.add(method.getValue().path("operationId").asText()))
                                .isTrue();
                        actual.add(method.getKey() + " " + path.getKey());
                    }
                }));
        Set<String> expected = new HashSet<>(Set.of(
                "post /api/v1/catalog/authorization-probes",
                "get /actuator/health/liveness",
                "get /actuator/health/readiness"));
        for (Operation operation : OPERATIONS) {
            expected.add(operation.method() + " " + operation.path());
            JsonNode node = document.path("paths").path(operation.path()).path(operation.method());
            assertThat(node.path("operationId").asText()).isEqualTo(operation.id());
            assertThat(node.path("security").get(0).has("catalogBearer")).isTrue();
            assertThat(node.path("x-max-header-bytes").asInt()).isEqualTo(16384);
            boolean mutation = !operation.request().isEmpty();
            assertThat(node.has("requestBody")).isEqualTo(mutation);
            assertThat(node.path("x-max-body-bytes").asInt()).isEqualTo(mutation ? 262144 : 0);
            List<JsonNode> parameters = new ArrayList<>();
            node.path("parameters").forEach(parameter -> parameters.add(CatalogContract.resolve(document, parameter)));
            assertThat(parameters.stream()
                            .anyMatch(
                                    parameter -> parameter.path("name").asText().equals("Idempotency-Key")))
                    .isEqualTo(mutation);
            for (JsonNode parameter : parameters) {
                if (parameter.path("in").asText().equals("path")
                        || parameter.path("in").asText().equals("header")) {
                    assertThat(parameter.path("required").asBoolean()).isTrue();
                }
            }
            if (mutation) {
                assertThat(node.at("/requestBody/required").asBoolean()).isTrue();
                assertThat(node.at("/requestBody/content/application~1json/schema/$ref")
                                .asText())
                        .isEqualTo("#/components/schemas/" + operation.request());
            }
            JsonNode response = node.path("responses").path(operation.success());
            assertThat(response.at("/content/application~1json/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/" + operation.response());
            assertThat(response.path("headers").has("Idempotency-Replayed")).isEqualTo(mutation);
            assertThat(response.path("headers").has("Location"))
                    .isEqualTo(operation.success().equals("201"));
            assertThat(node.path("responses").has(operation.success().equals("201") ? "200" : "201"))
                    .isFalse();
            for (String status : List.of("400", "401", "403", "413", "429", "500", "503", "504")) {
                assertThat(node.path("responses").has(status))
                        .as("%s %s", operation.id(), status)
                        .isTrue();
            }
            assertThat(node.path("responses").has("415")).isEqualTo(mutation);
            node.path("responses").forEach(value -> {
                JsonNode resolved = CatalogContract.resolve(document, value);
                assertThat(CatalogContract.resolve(
                                        document, resolved.path("headers").path("Cache-Control"))
                                .at("/schema/const")
                                .asText())
                        .isEqualTo("no-store");
            });
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void probeAndHealthContractsIncludingReferencedSchemasRemainUnchanged() throws IOException {
        JsonNode document = CatalogContract.read();
        JsonNode baseline = CatalogContract.baseline();
        baseline.path("paths").fields().forEachRemaining(path -> {
            assertThat(document.path("paths").path(path.getKey())).isEqualTo(path.getValue());
            assertThat(CatalogContract.wireShape(
                            document, document.path("paths").path(path.getKey())))
                    .isEqualTo(CatalogContract.wireShape(baseline, path.getValue()));
        });
    }

    @Test
    void referencesResolveAndRequiredPropertiesExist() throws IOException {
        JsonNode document = CatalogContract.read();
        inspect(document, document);
    }

    private void inspect(JsonNode document, JsonNode node) {
        if (node.has("$ref")) {
            CatalogContract.resolve(document, node);
        }
        if (node.has("properties")) {
            node.path("required")
                    .forEach(required -> assertThat(node.path("properties").has(required.asText()))
                            .as("Required property %s exists", required.asText())
                            .isTrue());
        }
        if (node.isContainerNode()) {
            node.forEach(child -> inspect(document, child));
        }
    }

    @Test
    void requestObjectsAreClosedExceptForOpaqueLongDescription() throws IOException {
        JsonNode schemas = CatalogContract.read().at("/components/schemas");
        for (String name : List.of(
                "CreateDraftRequest",
                "ReplaceCandidateRequest",
                "LifecycleRequest",
                "Candidate",
                "CandidateVariant",
                "Money")) {
            assertThat(schemas.path(name).path("additionalProperties").isBoolean())
                    .isTrue();
            assertThat(schemas.path(name).path("additionalProperties").asBoolean())
                    .isFalse();
        }
        assertThat(schemas.path("LongDescription").path("additionalProperties").asBoolean())
                .isTrue();
        for (String name : List.of("ReplaceCandidateRequest", "LifecycleRequest")) {
            assertThat(schemas.path(name).path("required").toString()).contains("expectedVersion");
        }
        assertThat(schemas.path("CreateDraftRequest").path("properties").has("expectedVersion"))
                .isFalse();
        assertThat(schemas.at("/CatalogVersion/minimum").asInt()).isZero();
    }

    @Test
    void exactMoneyAndAggregateBoundsMatchApprovedDecisions() throws IOException {
        JsonNode document = CatalogContract.read();
        JsonNode schemas = document.at("/components/schemas");
        JsonNode amount = schemas.at("/Money/properties/amount");
        assertThat(amount.path("type").asText()).isEqualTo("string");
        Pattern pattern = Pattern.compile(amount.path("pattern").asText());
        for (String valid : List.of("0.01", "1", "1.2", "1299.00", "999999999.99")) {
            assertThat(pattern.matcher(valid).matches()).as(valid).isTrue();
        }
        for (String invalid :
                List.of("-1", "01", "1.000", "1e2", " 1", "1 ", ".01", "NaN", "Infinity", "1000000000", "1,000")) {
            assertThat(pattern.matcher(invalid).matches()).as(invalid).isFalse();
        }
        assertThat(amount.at("/not/enum").toString()).isEqualTo("[\"0\",\"0.0\",\"0.00\"]");
        assertThat(schemas.at("/Money/properties/currency/const").asText()).isEqualTo("INR");
        assertThat(schemas.at("/Candidate/properties/variants/minItems").asInt())
                .isEqualTo(1);
        assertThat(schemas.at("/Candidate/properties/variants/maxItems").asInt())
                .isEqualTo(100);
        assertThat(schemas.at("/Candidate/properties/imageUrls/maxItems").asInt())
                .isEqualTo(10);
        assertThat(schemas.at("/Candidate/properties/optionDimensions/maxItems").asInt())
                .isEqualTo(2);
        assertThat(schemas.at("/CandidateVariant/properties/optionValues/maxItems")
                        .asInt())
                .isEqualTo(2);
        assertThat(schemas.at("/LongDescription/x-max-serialized-utf8-bytes").asInt())
                .isEqualTo(32768);
        assertThat(schemas.at("/LongDescription/x-max-json-depth").asInt()).isEqualTo(10);
        assertThat(document.at("/components/parameters/ProductLimit/schema/default")
                        .asInt())
                .isEqualTo(20);
        assertThat(document.at("/components/parameters/CategoryLimit/schema/default")
                        .asInt())
                .isEqualTo(50);
        for (String parameter : List.of("ProductLimit", "CategoryLimit")) {
            assertThat(document.at("/components/parameters/" + parameter + "/schema/minimum")
                            .asInt())
                    .isEqualTo(1);
            assertThat(document.at("/components/parameters/" + parameter + "/schema/maximum")
                            .asInt())
                    .isEqualTo(50);
        }
    }

    @Test
    void everyMaintenanceProblemHasMatchingStatusCodeAndSafeExtensions() throws IOException {
        JsonNode document = CatalogContract.read();
        Set<String> codes = new HashSet<>();
        for (Operation operation : OPERATIONS) {
            document.path("paths")
                    .path(operation.path())
                    .path(operation.method())
                    .path("responses")
                    .fields()
                    .forEachRemaining(response -> {
                        if (Integer.parseInt(response.getKey()) < 400) {
                            return;
                        }
                        JsonNode resolved = CatalogContract.resolve(document, response.getValue());
                        JsonNode schema = resolved.at("/content/application~1problem+json/schema");
                        List<JsonNode> alternatives = new ArrayList<>();
                        if (schema.has("oneOf")) {
                            schema.path("oneOf").forEach(alternatives::add);
                        } else {
                            alternatives.add(schema);
                        }
                        for (JsonNode alternative : alternatives) {
                            JsonNode problem = CatalogContract.resolve(document, alternative);
                            assertThat(problem.at("/properties/status/const").asText())
                                    .isEqualTo(response.getKey());
                            String code = problem.at("/properties/code/const").asText();
                            codes.add(code);
                            assertThat(problem.at("/properties/type/const").asText())
                                    .isEqualTo("urn:commerce:problem:"
                                            + code.toLowerCase(java.util.Locale.ROOT)
                                                    .replace('_', '-'));
                            assertThat(problem.path("required").toString())
                                    .isEqualTo("[\"type\",\"title\",\"status\",\"code\"]");
                            assertThat(problem.at("/properties/violations/maxItems")
                                            .asInt())
                                    .isEqualTo(20);
                            assertThat(problem.at("/properties/violations/items/properties")
                                            .has("value"))
                                    .isFalse();
                        }
                        if (response.getKey().equals("429")) {
                            assertThat(resolved.path("headers").has("Retry-After"))
                                    .isTrue();
                        }
                    });
        }
        assertThat(codes)
                .containsExactlyInAnyOrder(
                        "INVALID_REQUEST",
                        "AUTHENTICATION_REQUIRED",
                        "FORBIDDEN",
                        "PRODUCT_NOT_FOUND",
                        "VARIANT_NOT_FOUND",
                        "VERSION_CONFLICT",
                        "LIFECYCLE_CONFLICT",
                        "IDEMPOTENCY_CONFLICT",
                        "VARIANT_ID_CONFLICT",
                        "VARIANT_IDENTITY_CONFLICT",
                        "REQUEST_TOO_LARGE",
                        "UNSUPPORTED_MEDIA_TYPE",
                        "CANDIDATE_INVALID",
                        "PUBLICATION_INCOMPLETE",
                        "RATE_LIMITED",
                        "DEPENDENCY_UNAVAILABLE",
                        "GATEWAY_TIMEOUT",
                        "INTERNAL_ERROR");
    }

    @Test
    void commandFailuresAreDeclaredAtTheCorrectOperations() throws IOException {
        JsonNode document = CatalogContract.read();
        for (Operation operation : OPERATIONS) {
            JsonNode responses = document.path("paths")
                    .path(operation.path())
                    .path(operation.method())
                    .path("responses");
            assertThat(responses.has("404")).isEqualTo(operation.path().contains("{productId}"));
            assertThat(responses.has("409")).isEqualTo(!operation.request().isEmpty());
            boolean validatesCandidate = Set.of(
                            "createCatalogDraft",
                            "replaceCatalogCandidate",
                            "publishCatalogProduct",
                            "republishCatalogProduct",
                            "reactivateCatalogProduct")
                    .contains(operation.id());
            assertThat(responses.has("422")).isEqualTo(validatesCandidate);
        }
        JsonNode key = document.at("/components/parameters/LifecycleIdempotencyKey/schema");
        assertThat(key.path("minLength").asInt()).isEqualTo(16);
        assertThat(key.path("maxLength").asInt()).isEqualTo(128);
        Pattern keyPattern = Pattern.compile(key.path("pattern").asText());
        assertThat(keyPattern.matcher("a".repeat(16)).matches()).isTrue();
        assertThat(keyPattern.matcher("a".repeat(128)).matches()).isTrue();
        for (String invalid :
                List.of("a".repeat(15), "a".repeat(129), "invalid key with spaces", "invalid+key/value")) {
            assertThat(keyPattern.matcher(invalid).matches()).isFalse();
        }
    }

    @Test
    void responseMoneyIsCanonicalAndExamplesNeverUseNumericAmounts() throws IOException {
        JsonNode document = CatalogContract.read();
        JsonNode schemas = document.at("/components/schemas");
        Pattern amount = Pattern.compile(
                schemas.at("/ResponseMoney/properties/amount/pattern").asText());
        assertThat(amount.matcher("1299.00").matches()).isTrue();
        for (String invalid : List.of("1299", "1299.0", "1299.000")) {
            assertThat(amount.matcher(invalid).matches()).isFalse();
        }
        assertThat(schemas.at("/MaintainerProduct/properties/candidate/$ref").asText())
                .isEqualTo("#/components/schemas/ResponseCandidate");
        assertThat(schemas.at("/ResponseCandidate/properties/variants/items/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ResponseCandidateVariant");
        assertThat(schemas.at("/ResponseCandidateVariant/properties/price/anyOf/0/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ResponseMoney");
        schemas.forEach(schema -> schema.path("examples").forEach(this::assertDecimalStrings));
    }

    private void assertDecimalStrings(JsonNode example) {
        if (example.has("amount")) {
            assertThat(example.path("amount").isTextual()).isTrue();
        }
        if (example.isContainerNode()) {
            example.forEach(this::assertDecimalStrings);
        }
    }

    private record Operation(String method, String path, String id, String request, String success, String response) {}
}
