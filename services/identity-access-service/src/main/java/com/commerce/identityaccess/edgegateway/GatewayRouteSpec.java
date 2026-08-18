package com.commerce.identityaccess.edgegateway;

import java.net.URI;
import java.time.Duration;

public record GatewayRouteSpec(
        String id,
        String method,
        String path,
        URI target,
        AccessClass access,
        Duration deadline,
        long maxRequestBytes) {

    public enum AccessClass {
        PUBLIC,
        CUSTOMER,
        MAINTAINER
    }
}
