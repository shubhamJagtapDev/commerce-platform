package com.commerce.identityaccess.edgegateway;

import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "gatewayRoutes")
public class GatewayRouteManifestEndpoint {

    private final GatewayRouteRegistry registry;

    public GatewayRouteManifestEndpoint(GatewayRouteRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> manifest() {
        List<Map<String, Object>> routes = registry.routes().stream()
                .map(route -> Map.<String, Object>of(
                        "id", route.id(),
                        "method", route.method(),
                        "path", route.path(),
                        "access", route.access(),
                        "deadlineMs", route.deadline().toMillis(),
                        "maxRequestBytes", route.maxRequestBytes()))
                .toList();
        return Map.of("routeCount", routes.size(), "routes", routes);
    }
}
