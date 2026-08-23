package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.models.ValidatedOidcPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Validates the signed identity contract and resolves exactly one supported Gate 2 actor kind. */
@Component
public final class OidcPrincipalValidator {
    private final AuthProperties properties;
    private final VersionedCryptoService cryptoService;
    private final Clock clock;

    public OidcPrincipalValidator(AuthProperties properties, VersionedCryptoService cryptoService, Clock clock) {
        this.properties = properties;
        this.cryptoService = cryptoService;
        this.clock = clock;
    }

    public ValidatedOidcPrincipal validate(OidcUser user, String rawNonce) {
        List<String> audience = user.getIdToken().getAudience();
        String subject = user.getSubject();
        if (audience == null || subject == null) {
            throw new AuthenticationFailureException("missing_id_token_identity_claim");
        }
        if (!properties.publicIssuer().equals(String.valueOf(user.getIdToken().getIssuer()))
                || !audience.contains(properties.clientId())
                || !properties.clientId().equals(user.getIdToken().getClaimAsString("azp"))
                || user.getIdToken().getExpiresAt() == null
                || !user.getIdToken().getExpiresAt().isAfter(clock.instant())
                || notBefore(user).isAfter(clock.instant())
                || !cryptoService.sha256Url(rawNonce).equals(user.getIdToken().getClaimAsString("nonce"))) {
            throw new AuthenticationFailureException();
        }
        PrincipalKind kind = principalKind(user.getClaim("realm_access"));
        Set<String> authorities =
                switch (kind) {
                    case CUSTOMER -> Set.of("ROLE_CUSTOMER");
                    case CATALOG_MAINTAINER -> Set.of("ROLE_CATALOG_MAINTAINER");
                };
        return new ValidatedOidcPrincipal(
                properties.publicIssuer(), subject, user.getClaimAsString("sid"), kind, authorities);
    }

    private Instant notBefore(OidcUser user) {
        Instant notBefore = user.getIdToken().getClaimAsInstant("nbf");
        return notBefore == null ? Instant.MIN : notBefore;
    }

    private PrincipalKind principalKind(@Nullable Object realmAccess) {
        if (!(realmAccess instanceof Map<?, ?> claims) || !(claims.get("roles") instanceof Iterable<?> roleValues)) {
            throw new AuthenticationFailureException("unsupported_actor_role");
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
