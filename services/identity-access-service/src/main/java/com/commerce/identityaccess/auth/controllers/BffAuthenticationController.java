package com.commerce.identityaccess.auth.controllers;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.filters.BffSessionAuthenticationFilter;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.repositories.DatabaseAuthorizationRequestRepository;
import com.commerce.identityaccess.auth.services.BffSessionService;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class BffAuthenticationController {
    private final DatabaseAuthorizationRequestRepository authorizationRequestRepository;
    private final VersionedCryptoService cryptoService;
    private final AuthProperties properties;

    public BffAuthenticationController(
            DatabaseAuthorizationRequestRepository authorizationRequestRepository,
            VersionedCryptoService cryptoService,
            AuthProperties properties) {
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.cryptoService = cryptoService;
        this.properties = properties;
    }

    @GetMapping("/bff/login")
    void beginLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String state = cryptoService.randomUrlValue();
        String nonce = cryptoService.randomUrlValue();
        String verifier = cryptoService.randomUrlValue();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(properties.publicIssuer() + "/protocol/openid-connect/auth")
                .clientId(properties.clientId())
                .redirectUri(properties.publicOrigin() + "/login/oauth2/code/keycloak")
                .scopes(Set.of("openid", "roles"))
                .state(state)
                .attributes(attributes -> {
                    attributes.put("registration_id", "keycloak");
                    attributes.put("nonce", nonce);
                    attributes.put("code_verifier", verifier);
                })
                .additionalParameters(Map.of(
                        "nonce",
                        cryptoService.sha256Url(nonce),
                        "code_challenge",
                        cryptoService.sha256Url(verifier),
                        "code_challenge_method",
                        "S256"))
                .build();
        authorizationRequestRepository.create(state, nonce, verifier);
        response.sendRedirect(authorizationRequest.getAuthorizationRequestUri());
    }

    @GetMapping("/bff/csrf")
    ResponseEntity<Map<String, String>> csrf(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PrincipalContext)) {
            throw new MissingSessionException();
        }
        Object resolved = request.getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE);
        if (!(resolved instanceof BffSessionService.ResolvedSession session)) {
            throw new MissingSessionException();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", session.csrfToken()));
    }
}
