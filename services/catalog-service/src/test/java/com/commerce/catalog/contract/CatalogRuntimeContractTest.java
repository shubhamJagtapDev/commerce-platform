package com.commerce.catalog.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.catalog.catalogsecurity.CreateAuthorizationProbe;
import com.commerce.catalog.config.OpenApiConfiguration;
import com.commerce.catalog.config.SecurityConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Deliberately red until the real maintenance controllers encode the approved contract.
// No test-only maintenance handlers or static OpenAPI injection can satisfy this gate.
@Tag("catalog-runtime-contract")
@WebMvcTest(properties = "springdoc.api-docs.version=OPENAPI_3_1")
@Import({SecurityConfiguration.class, OpenApiConfiguration.class})
@ImportAutoConfiguration({
    SpringDocConfigProperties.class,
    SpringDocConfiguration.class,
    SpringDocWebMvcConfiguration.class,
    SpringDocSpecPropertiesConfiguration.class
})
class CatalogRuntimeContractTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateAuthorizationProbe createAuthorizationProbe;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void generatedSpringdocMaintenanceOperationsMatchTheCheckedInAuthority() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode generated = new ObjectMapper().readTree(json);
        // Establish that real controllers were discovered before reporting missing future ones.
        assertThat(generated.path("paths").has("/api/v1/catalog/authorization-probes"))
                .isTrue();
        JsonNode approved = CatalogContract.read();
        List<String> missing = new ArrayList<>();
        approved.path("paths").fields().forEachRemaining(path -> {
            if (path.getKey().startsWith(CatalogContract.PREFIX)) {
                path.getValue().fields().forEachRemaining(operation -> {
                    if (!generated.path("paths").path(path.getKey()).has(operation.getKey())) {
                        missing.add(operation.getKey().toUpperCase(java.util.Locale.ROOT) + " " + path.getKey());
                    }
                });
            }
        });
        assertThat(missing)
                .as("Approved maintenance handlers absent from generated Springdoc")
                .isEmpty();
        approved.path("paths").fields().forEachRemaining(path -> {
            if (path.getKey().startsWith(CatalogContract.PREFIX)) {
                assertThat(CatalogContract.wireShape(
                                generated, generated.path("paths").path(path.getKey())))
                        .as("Wire contract for %s", path.getKey())
                        .isEqualTo(CatalogContract.wireShape(approved, path.getValue()));
            }
        });
        generated.path("paths").fieldNames().forEachRemaining(path -> {
            if (path.startsWith(CatalogContract.PREFIX)) {
                assertThat(approved.path("paths").has(path))
                        .as("Unapproved maintenance path %s", path)
                        .isTrue();
            }
        });
    }
}
