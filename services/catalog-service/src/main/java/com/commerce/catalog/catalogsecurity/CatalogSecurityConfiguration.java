package com.commerce.catalog.catalogsecurity;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogSecurityProperties.class)
public class CatalogSecurityConfiguration {
    @Bean
    Clock catalogClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder catalogJwtDecoder(CatalogSecurityProperties properties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> catalogClaims = token -> hasCatalogClaims(token, properties)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "The token is not intended for Catalog", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, catalogClaims));
        return decoder;
    }

    private boolean hasCatalogClaims(Jwt token, CatalogSecurityProperties properties) {
        List<String> audience = Objects.requireNonNull(token.getAudience());
        return token.getIssuer() != null
                && token.getSubject() != null
                && !token.getSubject().isBlank()
                && audience.contains(properties.audience())
                && properties.authorizedParty().equals(token.getClaimAsString("azp"));
    }
}
