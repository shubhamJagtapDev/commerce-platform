package com.commerce.identityaccess.auth.configs;

import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthReadinessHealthIndicators {
    @Bean
    HealthIndicator crypto(VersionedCryptoService cryptoService) {
        return () -> Health.up().build();
    }

    @Bean
    HealthIndicator keycloak(AuthProperties properties) {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return () ->
                reachable(client, properties.privateDiscoveryUri()) && reachable(client, properties.privateJwksUri())
                        ? Health.up().build()
                        : Health.down().build();
    }

    private boolean reachable(HttpClient client, String target) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception exception) {
            return false;
        }
    }
}
