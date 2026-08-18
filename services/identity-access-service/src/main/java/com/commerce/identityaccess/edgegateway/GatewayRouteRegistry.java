package com.commerce.identityaccess.edgegateway;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class GatewayRouteRegistry implements InitializingBean {

    private final GatewayRouteProperties properties;

    public GatewayRouteRegistry(GatewayRouteProperties properties) {
        this.properties = properties;
    }

    public List<GatewayRouteSpec> routes() {
        return properties.routes();
    }

    @Override
    public void afterPropertiesSet() {
        GatewayRouteValidator.validate(routes());
    }
}
