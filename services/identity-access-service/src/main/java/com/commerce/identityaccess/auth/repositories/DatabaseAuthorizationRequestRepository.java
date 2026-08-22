package com.commerce.identityaccess.auth.repositories;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/** Stores the one-time authorization request server-side; no OAuth state is placed in an HTTP session. */
@Component
public final class DatabaseAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String NONCE_ATTRIBUTE = DatabaseAuthorizationRequestRepository.class.getName() + ".nonce";
    private final AuthTransactionStore transactionStore;
    private final AuthProperties properties;

    public DatabaseAuthorizationRequestRepository(AuthTransactionStore transactionStore, AuthProperties properties) {
        this.transactionStore = transactionStore;
        this.properties = properties;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }
        return rebuild(state, transactionStore.findUsable(state));
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;
        }
        String state = authorizationRequest.getState();
        String nonce = authorizationRequest.getAttribute("nonce");
        String verifier = authorizationRequest.getAttribute("code_verifier");
        if (state == null || nonce == null || verifier == null) {
            throw new AuthenticationFailureException();
        }
        create(state, nonce, verifier);
    }

    public void create(String state, String nonce, String verifier) {
        transactionStore.create(state, nonce, verifier);
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter("state");
        if (state == null || state.isBlank()) {
            return null;
        }
        AuthTransactionStore.TransactionMaterial material = transactionStore.claim(state);
        request.setAttribute(NONCE_ATTRIBUTE, material.nonce());
        return rebuild(state, material);
    }

    private OAuth2AuthorizationRequest rebuild(String state, AuthTransactionStore.TransactionMaterial material) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(properties.publicIssuer() + "/protocol/openid-connect/auth")
                .clientId(properties.clientId())
                .redirectUri(properties.publicOrigin() + "/login/oauth2/code/keycloak")
                .state(state)
                .attributes(attributes -> {
                    attributes.put("code_verifier", material.verifier());
                    attributes.put("nonce", material.nonce());
                    attributes.put("registration_id", "keycloak");
                })
                .additionalParameters(Map.of("nonce", material.nonce()))
                .build();
    }
}
