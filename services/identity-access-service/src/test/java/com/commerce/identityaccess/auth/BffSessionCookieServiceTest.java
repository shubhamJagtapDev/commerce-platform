package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.services.BffSessionCookieService;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class BffSessionCookieServiceTest {
    @Test
    void developmentCookieUsesOnlyTheDocumentedLocalhostException() {
        BffSessionCookieService cookies = new BffSessionCookieService(properties("commerce-session", false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.issue(response, "opaque-handle");

        String setCookie = Objects.requireNonNull(response.getHeader("Set-Cookie"));
        assertTrue(setCookie.contains("commerce-session=opaque-handle"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertFalse(setCookie.contains("Secure"));
        assertFalse(setCookie.contains("Domain"));
    }

    @Test
    void productionCookieUsesHostPrefixAndSecureAttribute() {
        BffSessionCookieService cookies = new BffSessionCookieService(properties("__Host-commerce-session", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookies.issue(response, "opaque-handle");

        String setCookie = Objects.requireNonNull(response.getHeader("Set-Cookie"));
        assertTrue(setCookie.contains("__Host-commerce-session=opaque-handle"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("Path=/"));
        assertFalse(setCookie.contains("Domain"));
    }

    private AuthProperties properties(String cookieName, boolean secureCookie) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        return new AuthProperties(
                "http://localhost:8082/realms/commerce",
                "identity-access-bff",
                "http://localhost:8080",
                "http://keycloak:8080/realms/commerce/.well-known/openid-configuration",
                "http://keycloak:8080/realms/commerce/protocol/openid-connect/certs",
                cookieName,
                secureCookie,
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofHours(8),
                new AuthProperties.Crypto("local-aes-2026-01", key, "local-hmac-2026-01", key));
    }
}
