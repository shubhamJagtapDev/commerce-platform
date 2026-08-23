package com.commerce.catalog.catalogsecurity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.commerce.catalog.config.SecurityConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CatalogAuthorizationProbeController.class)
@Import(SecurityConfiguration.class)
class CatalogAuthorizationProbeControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateAuthorizationProbe createAuthorizationProbe;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void anonymousRequestIsRejectedBeforeTheUseCase() throws Exception {
        mvc.perform(post("/api/v1/catalog/authorization-probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "0123456789abcdef")
                        .content("{\"purpose\":\"COM_46_AUTHORIZATION_GATE\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(createAuthorizationProbe);
    }

    @Test
    void authenticatedCatalogTokenReturnsTheCommittedProbe() throws Exception {
        UUID probeId = UUID.randomUUID();
        when(createAuthorizationProbe.create(any(), any(), any(), any()))
                .thenReturn(
                        new CreateAuthorizationProbe.Result(probeId, 0, Instant.parse("2026-08-23T10:15:30Z"), true));

        mvc.perform(post("/api/v1/catalog/authorization-probes")
                        .with(jwt().jwt(token ->
                                token.issuer("http://issuer.example").subject("maintainer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "0123456789abcdef")
                        .content("{\"purpose\":\"COM_46_AUTHORIZATION_GATE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.probeId").value(probeId.toString()))
                .andExpect(jsonPath("$.version").value(0));
    }
}
