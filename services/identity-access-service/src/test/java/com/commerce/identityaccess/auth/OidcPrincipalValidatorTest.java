package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.models.ValidatedOidcPrincipal;
import com.commerce.identityaccess.auth.services.OidcPrincipalValidator;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class OidcPrincipalValidatorTest {
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");
    private static final String RAW_NONCE = "raw-nonce";

    @Test
    void customerRoleCreatesTheCustomerPrincipal() {
        ValidatedOidcPrincipal principal = validator().validate(user(List.of("CUSTOMER"), RAW_NONCE), RAW_NONCE);

        assertEquals(PrincipalKind.CUSTOMER, principal.kind());
        assertEquals(java.util.Set.of("ROLE_CUSTOMER"), principal.authorities());
    }

    @Test
    void maintainerRoleCreatesTheMaintainerPrincipal() {
        ValidatedOidcPrincipal principal =
                validator().validate(user(List.of("CATALOG_MAINTAINER"), RAW_NONCE), RAW_NONCE);

        assertEquals(PrincipalKind.CATALOG_MAINTAINER, principal.kind());
        assertEquals(java.util.Set.of("ROLE_CATALOG_MAINTAINER"), principal.authorities());
    }

    @Test
    void missingActorRoleIsRejected() {
        AuthenticationFailureException failure = assertThrows(
                AuthenticationFailureException.class,
                () -> validator().validate(user(List.of("unrelated-role"), RAW_NONCE), RAW_NONCE));

        assertEquals("unsupported_actor_role", failure.code());
    }

    @Test
    void mixedActorRolesAreRejected() {
        AuthenticationFailureException failure = assertThrows(
                AuthenticationFailureException.class,
                () -> validator().validate(user(List.of("CUSTOMER", "CATALOG_MAINTAINER"), RAW_NONCE), RAW_NONCE));

        assertEquals("unsupported_actor_role", failure.code());
    }

    @Test
    void mismatchedNonceIsRejected() {
        assertThrows(
                AuthenticationFailureException.class,
                () -> validator().validate(user(List.of("CUSTOMER"), "different-nonce"), RAW_NONCE));
    }

    private OidcPrincipalValidator validator() {
        AuthProperties properties = properties();
        return new OidcPrincipalValidator(
                properties, new VersionedCryptoService(properties), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OidcUser user(List<String> roles, String tokenNonceSource) {
        AuthProperties properties = properties();
        VersionedCryptoService cryptoService = new VersionedCryptoService(properties);
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", properties.publicIssuer());
        claims.put("sub", "synthetic-subject");
        claims.put("aud", List.of(properties.clientId()));
        claims.put("azp", properties.clientId());
        claims.put("nonce", cryptoService.sha256Url(tokenNonceSource));
        claims.put("sid", "oidc-session");
        claims.put("realm_access", Map.of("roles", roles));
        OidcIdToken idToken = new OidcIdToken("signed-id-token", NOW.minusSeconds(30), NOW.plusSeconds(300), claims);
        return new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
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
                new AuthProperties.Registration(true, 5, Duration.ofHours(1), Duration.ofMinutes(10), key),
                new AuthProperties.Crypto("local-aes-2026-01", key, "local-hmac-2026-01", key));
    }
}
