package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.services.OidcAuthorizationRequestFactory;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;

class OidcAuthorizationRequestFactoryTest {
    @Test
    void restoredRequestPreservesTheOidcAndPkceContract() {
        VersionedCryptoService cryptoService = new VersionedCryptoService(properties());
        OidcAuthorizationRequestFactory factory = new OidcAuthorizationRequestFactory(properties(), cryptoService);

        OAuth2AuthorizationRequest request = factory.restore("state", "nonce", "verifier");

        assertEquals(Set.of("openid", "roles"), request.getScopes());
        assertEquals("keycloak", request.getAttribute(OAuth2ParameterNames.REGISTRATION_ID));
        assertEquals("nonce", request.getAttribute(OidcParameterNames.NONCE));
        assertEquals("verifier", request.getAttribute(PkceParameterNames.CODE_VERIFIER));
        assertEquals(
                cryptoService.sha256Url("nonce"),
                request.getAdditionalParameters().get("nonce"));
        assertEquals(
                cryptoService.sha256Url("verifier"),
                request.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE));
        assertEquals("S256", request.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD));
    }

    @Test
    void newLoginRequestContainsFreshTransactionMaterial() {
        OidcAuthorizationRequestFactory factory =
                new OidcAuthorizationRequestFactory(properties(), new VersionedCryptoService(properties()));

        OAuth2AuthorizationRequest request = factory.createLoginRequest();

        assertNotNull(request.getState());
        assertNotNull(request.getAttribute(OidcParameterNames.NONCE));
        assertNotNull(request.getAttribute(PkceParameterNames.CODE_VERIFIER));
    }

    private AuthProperties properties() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new AuthProperties(
                "http://localhost:8082/realms/commerce",
                "identity-access-bff",
                "http://localhost:8080",
                "http://keycloak:8080/realms/commerce/.well-known/openid-configuration",
                "http://keycloak:8080/realms/commerce/protocol/openid-connect/certs",
                "commerce-session",
                false,
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofHours(8),
                new AuthProperties.Crypto("local-aes-2026-01", key, "local-hmac-2026-01", key));
    }
}
