package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** Terminates failed OIDC callbacks without exposing provider or credential material. */
@Component
public final class SafeOidcAuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeOidcAuthenticationFailureHandler.class);
    private static final String AUTHENTICATION_PROBLEM =
            "{\"type\":\"urn:commerce:problem:authentication-failed\",\"title\":\"Authentication failed\",\"status\":401,\"code\":\"AUTHENTICATION_FAILED\"}";
    private static final String DEPENDENCY_PROBLEM =
            "{\"type\":\"urn:commerce:problem:dependency-unavailable\",\"title\":\"Dependency unavailable\",\"status\":503,\"code\":\"DEPENDENCY_UNAVAILABLE\"}";

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String code = exception instanceof OAuth2AuthenticationException oauthFailure
                ? oauthFailure.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        if (exception instanceof AuthenticationServiceException
                || "server_error".equals(code)
                || "temporarily_unavailable".equals(code)) {
            rejectDependencyUnavailable(response, code);
            return;
        }
        rejectAuthentication(response, code);
    }

    public void reject(HttpServletResponse response, AuthenticationFailureException exception) throws IOException {
        rejectAuthentication(response, exception.code());
    }

    public void rejectDependencyUnavailable(HttpServletResponse response, Throwable failure) throws IOException {
        rejectDependencyUnavailable(response, failure.getClass().getSimpleName());
    }

    private void rejectAuthentication(HttpServletResponse response, String code) throws IOException {
        LOGGER.info("OIDC callback authentication rejected with code={}", code);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(AUTHENTICATION_PROBLEM);
    }

    private void rejectDependencyUnavailable(HttpServletResponse response, String code) throws IOException {
        LOGGER.warn("OIDC callback dependency unavailable with code={}", code);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(DEPENDENCY_PROBLEM);
    }
}
