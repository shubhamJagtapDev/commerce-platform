package com.commerce.identityaccess.auth.configs;

import com.commerce.identityaccess.auth.filters.BffSessionAuthenticationFilter;
import com.commerce.identityaccess.auth.services.BffSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/** Derives the synchronizer token from the opaque handle and verifies it against the stored session. */
public final class SessionBoundCsrfTokenRepository implements CsrfTokenRepository {
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken("X-CSRF-Token", "_csrf", "unavailable");
    }

    @Override
    public void saveToken(@Nullable CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // The BFF session is the sole durable CSRF owner; this repository never creates an HTTP session.
    }

    @Override
    public @Nullable CsrfToken loadToken(HttpServletRequest request) {
        Object resolved = request.getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE);
        if (!(resolved instanceof BffSessionService.ResolvedSession session)) {
            return null;
        }
        return new DefaultCsrfToken("X-CSRF-Token", "_csrf", session.csrfToken());
    }
}
