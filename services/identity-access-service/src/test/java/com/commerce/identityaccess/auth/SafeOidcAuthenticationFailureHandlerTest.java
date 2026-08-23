package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.services.SafeOidcAuthenticationFailureHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SafeOidcAuthenticationFailureHandlerTest {
    private final SafeOidcAuthenticationFailureHandler failureHandler = new SafeOidcAuthenticationFailureHandler();

    @Test
    void validationFailureReturnsOnlyTheSafeNoStoreProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.reject(response, new AuthenticationFailureException("unsupported_actor_role"));

        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(
                "{\"type\":\"urn:commerce:problem:authentication-failed\",\"title\":\"Authentication failed\",\"status\":401}",
                response.getContentAsString());
        assertFalse(response.getContentAsString().contains("unsupported_actor_role"));
    }
}
