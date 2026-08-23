package com.commerce.identityaccess.auth.models;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Identity accepted from a signed OIDC ID token before a BFF session is created. */
public record ValidatedOidcPrincipal(
        String issuer, String subject, @Nullable String oidcSessionId, PrincipalKind kind, Set<String> authorities) {
    public ValidatedOidcPrincipal {
        authorities = Set.copyOf(authorities);
    }
}
