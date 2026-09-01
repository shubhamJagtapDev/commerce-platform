package com.commerce.identityaccess.auth.repositories;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.AuthFlowKind;
import com.commerce.identityaccess.auth.services.OidcAuthorizationRequestFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;

/** Stores the one-time authorization request server-side; no OAuth state is placed in an HTTP session. */
@Component
public final class DatabaseAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    public static final String NONCE_ATTRIBUTE = DatabaseAuthorizationRequestRepository.class.getName() + ".nonce";
    private final AuthTransactionStore transactionStore;
    private final OidcAuthorizationRequestFactory authorizationRequestFactory;

    public DatabaseAuthorizationRequestRepository(
            AuthTransactionStore transactionStore, OidcAuthorizationRequestFactory authorizationRequestFactory) {
        this.transactionStore = transactionStore;
        this.authorizationRequestFactory = authorizationRequestFactory;
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
        String nonce = authorizationRequest.getAttribute(OidcParameterNames.NONCE);
        String verifier = authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER);
        Object flowKind = authorizationRequest.getAttribute(OidcAuthorizationRequestFactory.FLOW_KIND_ATTRIBUTE);
        if (state == null || nonce == null || verifier == null || !(flowKind instanceof AuthFlowKind kind)) {
            throw new AuthenticationFailureException();
        }
        transactionStore.create(state, nonce, verifier, kind);
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
        request.setAttribute(OidcAuthorizationRequestFactory.FLOW_KIND_ATTRIBUTE, material.flowKind());
        return rebuild(state, material);
    }

    private OAuth2AuthorizationRequest rebuild(String state, AuthTransactionStore.TransactionMaterial material) {
        return authorizationRequestFactory.restore(state, material.nonce(), material.verifier(), material.flowKind());
    }
}
