package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.AuthFlowKind;
import com.commerce.identityaccess.auth.models.CreatedBffSession;
import com.commerce.identityaccess.auth.models.OidcTokenBundle;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.models.ValidatedOidcPrincipal;
import com.commerce.identityaccess.auth.repositories.DatabaseAuthorizationRequestRepository;
import com.commerce.identityaccess.auth.repositories.RequestAuthorizedClientRepository;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerAccountBindingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public final class OidcBffAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private final RequestAuthorizedClientRepository authorizedClientRepository;
    private final BffSessionService sessionService;
    private final BffSessionCookieService cookieService;
    private final SafeOidcAuthenticationFailureHandler failureHandler;
    private final OidcPrincipalValidator principalValidator;

    public OidcBffAuthenticationSuccessHandler(
            RequestAuthorizedClientRepository authorizedClientRepository,
            BffSessionService sessionService,
            BffSessionCookieService cookieService,
            SafeOidcAuthenticationFailureHandler failureHandler,
            OidcPrincipalValidator principalValidator) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
        this.failureHandler = failureHandler;
        this.principalValidator = principalValidator;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        try {
            completeAuthentication(request, response, authentication);
        } catch (AuthenticationFailureException exception) {
            failureHandler.reject(response, exception);
        } catch (CustomerAccountBindingException exception) {
            failureHandler.reject(response, new AuthenticationFailureException("customer_account_binding_failed"));
        } catch (DataAccessException exception) {
            failureHandler.rejectDependencyUnavailable(response, exception);
        }
    }

    private void completeAuthentication(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthAuthentication)
                || !(oauthAuthentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new AuthenticationFailureException("unexpected_oidc_authentication");
        }
        OAuth2AuthorizedClient client = authorizedClientRepository.current(request);
        if (client == null) {
            throw new AuthenticationFailureException("missing_callback_authorized_client");
        }
        ValidatedOidcPrincipal principal = principalValidator.validate(oidcUser, claimedNonce(request));
        AuthFlowKind flowKind = claimedFlowKind(request);
        if (flowKind == AuthFlowKind.CUSTOMER_REGISTRATION && principal.kind() != PrincipalKind.CUSTOMER) {
            throw new AuthenticationFailureException("registration_actor_must_be_customer");
        }
        String refreshToken = client.getRefreshToken() == null
                ? null
                : client.getRefreshToken().getTokenValue();
        CreatedBffSession createdSession = sessionService.create(
                principal,
                new OidcTokenBundle(
                        oidcUser.getIdToken().getTokenValue(),
                        client.getAccessToken().getTokenValue(),
                        refreshToken));
        cookieService.issue(response, createdSession.rawHandle());
        response.sendRedirect("/bff/csrf");
    }

    private String claimedNonce(HttpServletRequest request) {
        Object expected = request.getAttribute(DatabaseAuthorizationRequestRepository.NONCE_ATTRIBUTE);
        if (!(expected instanceof String nonce)) {
            throw new AuthenticationFailureException("missing_callback_nonce");
        }
        return nonce;
    }

    private AuthFlowKind claimedFlowKind(HttpServletRequest request) {
        Object flowKind = request.getAttribute(OidcAuthorizationRequestFactory.FLOW_KIND_ATTRIBUTE);
        if (!(flowKind instanceof AuthFlowKind kind)) {
            throw new AuthenticationFailureException("missing_callback_flow_kind");
        }
        return kind;
    }
}
