package com.commerce.identityaccess.auth.repositories;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

/** Keeps OAuth client material only for the callback request; BFF session persistence owns durable storage. */
@Component
public final class RequestAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    static final String AUTHORIZED_CLIENT_ATTRIBUTE = RequestAuthorizedClientRepository.class.getName() + ".client";

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        request.setAttribute(AUTHORIZED_CLIENT_ATTRIBUTE, authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        request.removeAttribute(AUTHORIZED_CLIENT_ATTRIBUTE);
    }

    public @Nullable OAuth2AuthorizedClient current(HttpServletRequest request) {
        Object client = request.getAttribute(AUTHORIZED_CLIENT_ATTRIBUTE);
        return client instanceof OAuth2AuthorizedClient authorizedClient ? authorizedClient : null;
    }
}
