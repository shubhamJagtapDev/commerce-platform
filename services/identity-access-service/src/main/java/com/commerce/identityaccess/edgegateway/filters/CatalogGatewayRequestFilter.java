package com.commerce.identityaccess.edgegateway.filters;

import com.commerce.identityaccess.auth.filters.BffSessionAuthenticationFilter;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.ResolvedBffSession;
import com.commerce.identityaccess.auth.services.BffSessionService;
import com.commerce.identityaccess.edgegateway.GatewayRequestRejectedException;
import com.commerce.identityaccess.edgegateway.config.GatewayRouteSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;

public final class CatalogGatewayRequestFilter implements Function<ServerRequest, ServerRequest> {
    private static final List<String> RELAYED_HEADERS =
            List.of(HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, "Idempotency-Key");

    private final GatewayRouteSpec route;
    private final BffSessionService sessionService;

    public CatalogGatewayRequestFilter(GatewayRouteSpec route, BffSessionService sessionService) {
        this.route = route;
        this.sessionService = sessionService;
    }

    @Override
    public ServerRequest apply(ServerRequest request) {
        enforceLimits(request);
        String accessToken = sessionService.resolveMaintainerAccessToken(maintainerPrincipal(request));
        return sanitizedRequest(request, accessToken);
    }

    private void enforceLimits(ServerRequest request) {
        long contentLength = request.headers().contentLength().orElse(-1);
        if (contentLength < 0 || contentLength > route.maxRequestBytes()) {
            throw new GatewayRequestRejectedException(HttpStatus.CONTENT_TOO_LARGE, "REQUEST_TOO_LARGE");
        }
        long headerBytes = request.headers().asHttpHeaders().headerSet().stream()
                .mapToLong(entry -> entry.getKey().getBytes(StandardCharsets.UTF_8).length
                        + entry.getValue().stream()
                                .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
                                .sum())
                .sum();
        if (headerBytes > route.maxHeaderBytes()) {
            throw new GatewayRequestRejectedException(HttpStatus.CONTENT_TOO_LARGE, "REQUEST_TOO_LARGE");
        }
    }

    private PrincipalContext maintainerPrincipal(ServerRequest request) {
        Object resolved = request.attribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE)
                .orElse(null);
        if (!(resolved instanceof ResolvedBffSession session)) {
            throw new GatewayRequestRejectedException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        PrincipalContext principal = session.principal();
        if (!principal.authorities().contains("ROLE_CATALOG_MAINTAINER")) {
            throw new GatewayRequestRejectedException(HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        return principal;
    }

    private ServerRequest sanitizedRequest(ServerRequest request, String accessToken) {
        HttpHeaders approved = new HttpHeaders();
        for (String header : RELAYED_HEADERS) {
            approved.put(header, request.headers().header(header));
        }
        approved.setBearerAuth(accessToken);
        approved.set("X-Correlation-Id", UUID.randomUUID().toString());
        approved.set("X-Request-Deadline-Millis", Long.toString(route.deadline().toMillis()));
        return ServerRequest.from(request)
                .headers(headers -> {
                    headers.clear();
                    headers.putAll(approved);
                })
                .build();
    }
}
