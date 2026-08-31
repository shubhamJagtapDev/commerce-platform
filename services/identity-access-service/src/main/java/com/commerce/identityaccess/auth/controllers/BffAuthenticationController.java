package com.commerce.identityaccess.auth.controllers;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.exceptions.RegistrationUnavailableException;
import com.commerce.identityaccess.auth.filters.BffSessionAuthenticationFilter;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.ResolvedBffSession;
import com.commerce.identityaccess.auth.repositories.DatabaseAuthorizationRequestRepository;
import com.commerce.identityaccess.auth.services.OidcAuthorizationRequestFactory;
import com.commerce.identityaccess.auth.services.RegistrationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class BffAuthenticationController {
    private final DatabaseAuthorizationRequestRepository authorizationRequestRepository;
    private final OidcAuthorizationRequestFactory authorizationRequestFactory;
    private final RegistrationRateLimiter registrationRateLimiter;
    private final AuthProperties properties;

    public BffAuthenticationController(
            DatabaseAuthorizationRequestRepository authorizationRequestRepository,
            OidcAuthorizationRequestFactory authorizationRequestFactory,
            RegistrationRateLimiter registrationRateLimiter,
            AuthProperties properties) {
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.authorizationRequestFactory = authorizationRequestFactory;
        this.registrationRateLimiter = registrationRateLimiter;
        this.properties = properties;
    }

    @GetMapping("/bff/register")
    void beginRegistration(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!properties.registration().enabled()) {
            throw new RegistrationUnavailableException();
        }
        registrationRateLimiter.check(request.getRemoteAddr());
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequestFactory.createRegistrationRequest();
        authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request, response);
        response.sendRedirect(authorizationRequest.getAuthorizationRequestUri());
    }

    @GetMapping("/bff/login")
    void beginLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequestFactory.createLoginRequest();
        authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request, response);
        response.sendRedirect(authorizationRequest.getAuthorizationRequestUri());
    }

    @GetMapping("/bff/csrf")
    ResponseEntity<Map<String, String>> csrf(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PrincipalContext)) {
            throw new MissingSessionException();
        }
        Object resolved = request.getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE);
        if (!(resolved instanceof ResolvedBffSession session)) {
            throw new MissingSessionException();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", session.csrfToken()));
    }
}
