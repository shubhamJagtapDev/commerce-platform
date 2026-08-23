package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** Terminates failed OIDC callbacks without exposing provider or credential material. */
@Component
public final class SafeOidcAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeOidcAuthenticationFailureHandler.class);
    private static final String PROBLEM_RESPONSE =
            "{\"type\":\"urn:commerce:problem:authentication-failed\",\"title\":\"Authentication failed\",\"status\":401}";

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauthFailure
                ? oauthFailure.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        reject(response, code);
    }

    public void reject(HttpServletResponse response, AuthenticationFailureException exception) throws IOException {
        reject(response, exception.code());
    }

    private void reject(HttpServletResponse response, String code) throws IOException {
        LOGGER.info("OIDC callback authentication rejected with code={}", code);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(PROBLEM_RESPONSE);
    }
}
