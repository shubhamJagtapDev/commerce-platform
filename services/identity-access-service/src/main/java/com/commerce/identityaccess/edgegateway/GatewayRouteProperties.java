package com.commerce.identityaccess.edgegateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commerce.gateway")
public record GatewayRouteProperties(List<GatewayRouteSpec> routes) {

    public GatewayRouteProperties {
        routes = routes == null ? List.of() : List.copyOf(routes);
    }
}
