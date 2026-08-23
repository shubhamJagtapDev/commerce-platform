package com.commerce.identityaccess.auth.filters;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces same-origin browser context before CSRF validation for future unsafe BFF routes. */
public final class UnsafeRequestOriginFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final AuthProperties properties;

    public UnsafeRequestOriginFilter(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (requiresOriginValidation(request) && !sameOrigin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");
            response.getWriter()
                    .write(
                            "{\"type\":\"urn:commerce:problem:csrf-rejected\",\"title\":\"Request rejected\",\"status\":403}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requiresOriginValidation(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod())
                && request.getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE) != null;
    }

    private boolean sameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        boolean originMatches = properties.publicOrigin().equals(origin);
        boolean refererMatches =
                origin == null && referer != null && referer.startsWith(properties.publicOrigin() + "/");
        boolean fetchMetadataMatches =
                fetchSite == null || Set.of("same-origin", "same-site", "none").contains(fetchSite);
        return fetchMetadataMatches && (originMatches || refererMatches);
    }
}
