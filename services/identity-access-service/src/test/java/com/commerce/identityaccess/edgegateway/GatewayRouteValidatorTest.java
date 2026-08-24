package com.commerce.identityaccess.edgegateway;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.identityaccess.edgegateway.config.GatewayRouteSpec;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayRouteValidatorTest {

    @Test
    void acceptsAnEmptyFoundationRegistry() {
        assertThatCode(() -> GatewayRouteValidator.validate(List.of())).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatThrownBy(() -> GatewayRouteValidator.validate(
                        List.of(route("catalog", "GET", "/a"), route("catalog", "POST", "/b"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsCatchAllPaths() {
        assertThatThrownBy(() -> GatewayRouteValidator.validate(List.of(route("catalog", "GET", "/api/catalog/**"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Catch-all");
    }

    @Test
    void rejectsAmbiguousTemplatedPaths() {
        assertThatThrownBy(() -> GatewayRouteValidator.validate(List.of(
                        route("by-id", "GET", "/api/catalog/{id}"), route("by-slug", "GET", "/api/catalog/{slug}"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous");
    }

    @Test
    void rejectsRoutesWithoutAdmissionOrHeaderLimits() {
        GatewayRouteSpec route = new GatewayRouteSpec(
                "catalog",
                "POST",
                "/api/v1/catalog/authorization-probes",
                URI.create("http://catalog-service:8081"),
                GatewayRouteSpec.AccessClass.MAINTAINER,
                Duration.ofSeconds(1),
                1024,
                0,
                0);

        assertThatThrownBy(() -> GatewayRouteValidator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limits");
    }

    private GatewayRouteSpec route(String id, String method, String path) {
        return new GatewayRouteSpec(
                id,
                method,
                path,
                URI.create("http://catalog-service:8081"),
                GatewayRouteSpec.AccessClass.MAINTAINER,
                Duration.ofSeconds(1),
                1_048_576,
                16_384,
                20);
    }
}
