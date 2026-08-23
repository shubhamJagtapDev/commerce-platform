package com.commerce.identityaccess.edgegateway;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import com.commerce.identityaccess.auth.services.BffSessionService;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.cloud.gateway.server.mvc.handler.RestClientProxyExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration(proxyBeanMethods = false)
public class CatalogGatewayConfiguration {
    @Bean
    ProxyExchange catalogGatewayProxyExchange(GatewayMvcProperties gatewayProperties) {
        Duration timeout = Duration.ofSeconds(1);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient restClient =
                RestClient.builder().requestFactory(requestFactory).build();
        return new RestClientProxyExchange(restClient, gatewayProperties);
    }

    @Bean
    RouterFunction<ServerResponse> catalogAuthorizationProbeRoute(
            GatewayRouteRegistry registry, BffSessionService sessionService) {
        if (registry.routes().size() != 1) {
            throw new IllegalStateException("COM-46 requires exactly one explicit Catalog gateway route");
        }
        GatewayRouteSpec specification = registry.routes().getFirst();
        CatalogGatewayRequestFilter requestFilter = new CatalogGatewayRequestFilter(specification, sessionService);
        GatewayAdmissionFilter admissionFilter = new GatewayAdmissionFilter(specification.admissionCapacity());
        return route(specification.id())
                .POST(specification.path(), http())
                .before(requestFilter)
                .before(uri(specification.target()))
                .filter(admissionFilter)
                .after(removeResponseHeader("Set-Cookie"))
                .after(removeResponseHeader("Server"))
                .build();
    }
}
