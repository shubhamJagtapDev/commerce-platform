package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.repositories.DatabaseAuthorizationRequestRepository;
import com.commerce.identityaccess.auth.repositories.RequestAuthorizedClientRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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
    private final AuthProperties properties;
    private final VersionedCryptoService cryptoService;
    private final Clock clock;

    public OidcBffAuthenticationSuccessHandler(
            RequestAuthorizedClientRepository authorizedClientRepository,
            BffSessionService sessionService,
            BffSessionCookieService cookieService,
            SafeOidcAuthenticationFailureHandler failureHandler,
            AuthProperties properties,
            VersionedCryptoService cryptoService,
            Clock clock) {
        this.authorizedClientRepository = authorizedClientRepository;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
        this.failureHandler = failureHandler;
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        try {
            completeAuthentication(request, response, authentication);
        } catch (AuthenticationFailureException exception) {
            failureHandler.reject(response, exception);
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
        BffSessionService.ValidatedOidcPrincipal principal = validate(oidcUser, request);
        String refreshToken = client.getRefreshToken() == null
                ? null
                : client.getRefreshToken().getTokenValue();
        String handle = sessionService.create(
                principal,
                new BffSessionService.OidcTokenBundle(
                        oidcUser.getIdToken().getTokenValue(),
                        client.getAccessToken().getTokenValue(),
                        refreshToken));
        cookieService.issue(response, handle);
        response.sendRedirect("/bff/csrf");
    }

    private BffSessionService.ValidatedOidcPrincipal validate(OidcUser user, HttpServletRequest request) {
        List<String> audience = user.getIdToken().getAudience();
        String subject = user.getSubject();
        @Nullable Object realmAccess = user.getClaim("realm_access");
        if (audience == null || subject == null) {
            throw new AuthenticationFailureException("missing_id_token_identity_claim");
        }
        if (!properties.publicIssuer().equals(String.valueOf(user.getIdToken().getIssuer()))
                || !audience.contains(properties.clientId())
                || !properties.clientId().equals(user.getIdToken().getClaimAsString("azp"))
                || user.getIdToken().getExpiresAt() == null
                || !user.getIdToken().getExpiresAt().isAfter(clock.instant())
                || notBefore(user).isAfter(clock.instant())
                || !equalsNonce(request, user)) {
            throw new AuthenticationFailureException();
        }
        PrincipalKind kind = principalKind(realmAccess);
        Set<String> authorities =
                switch (kind) {
                    case CUSTOMER -> Set.of("ROLE_CUSTOMER");
                    case CATALOG_MAINTAINER -> Set.of("ROLE_CATALOG_MAINTAINER");
                };
        return new BffSessionService.ValidatedOidcPrincipal(
                properties.publicIssuer(), subject, user.getClaimAsString("sid"), kind, authorities);
    }

    private boolean equalsNonce(HttpServletRequest request, OidcUser user) {
        Object expected = request.getAttribute(DatabaseAuthorizationRequestRepository.NONCE_ATTRIBUTE);
        return expected instanceof String nonce
                && cryptoService.sha256Url(nonce).equals(user.getIdToken().getClaimAsString("nonce"));
    }

    private Instant notBefore(OidcUser user) {
        Instant notBefore = user.getIdToken().getClaimAsInstant("nbf");
        return notBefore == null ? Instant.MIN : notBefore;
    }

    private PrincipalKind principalKind(@Nullable Object realmAccess) {
        if (!(realmAccess instanceof Map<?, ?> claims) || !(claims.get("roles") instanceof Iterable<?> roleValues)) {
            throw new AuthenticationFailureException();
        }
        boolean customer = false;
        boolean maintainer = false;
        for (Object roleValue : roleValues) {
            if ("CUSTOMER".equals(roleValue)) {
                customer = true;
            }
            if ("CATALOG_MAINTAINER".equals(roleValue)) {
                maintainer = true;
            }
        }
        if (customer == maintainer) {
            throw new AuthenticationFailureException("unsupported_actor_role");
        }
        return customer ? PrincipalKind.CUSTOMER : PrincipalKind.CATALOG_MAINTAINER;
    }
}
