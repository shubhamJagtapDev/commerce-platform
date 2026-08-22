package com.commerce.identityaccess.auth.filters;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.services.BffSessionCookieService;
import com.commerce.identityaccess.auth.services.BffSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BffSessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String RESOLVED_SESSION_ATTRIBUTE = BffSessionAuthenticationFilter.class.getName() + ".session";

    private final BffSessionService sessionService;
    private final BffSessionCookieService cookieService;

    public BffSessionAuthenticationFilter(BffSessionService sessionService, BffSessionCookieService cookieService) {
        this.sessionService = sessionService;
        this.cookieService = cookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String handle = cookieService.read(request);
        if (handle != null) {
            try {
                BffSessionService.ResolvedSession session = sessionService.resolve(handle);
                Collection<SimpleGrantedAuthority> authorities = session.principal().authorities().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        UsernamePasswordAuthenticationToken.authenticated(session.principal(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute(RESOLVED_SESSION_ATTRIBUTE, session);
            } catch (MissingSessionException exception) {
                cookieService.clear(response);
            }
        }
        filterChain.doFilter(request, response);
    }
}
