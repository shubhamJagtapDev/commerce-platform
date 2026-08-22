package com.commerce.identityaccess.config;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.configs.SessionBoundCsrfTokenRepository;
import com.commerce.identityaccess.auth.filters.BffSessionAuthenticationFilter;
import com.commerce.identityaccess.auth.filters.UnsafeRequestOriginFilter;
import com.commerce.identityaccess.auth.repositories.DatabaseAuthorizationRequestRepository;
import com.commerce.identityaccess.auth.repositories.RequestAuthorizedClientRepository;
import com.commerce.identityaccess.auth.services.BffSessionCookieService;
import com.commerce.identityaccess.auth.services.BffSessionService;
import com.commerce.identityaccess.auth.services.OidcBffAuthenticationSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            DatabaseAuthorizationRequestRepository authorizationRequestRepository,
            RequestAuthorizedClientRepository authorizedClientRepository,
            OidcBffAuthenticationSuccessHandler successHandler,
            BffSessionService sessionService,
            BffSessionCookieService cookieService,
            AuthProperties authProperties)
            throws Exception {
        BffSessionAuthenticationFilter sessionFilter =
                new BffSessionAuthenticationFilter(sessionService, cookieService);
        UnsafeRequestOriginFilter originFilter = new UnsafeRequestOriginFilter(authProperties);
        return http.securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(new SessionBoundCsrfTokenRepository()))
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
                .addFilterBefore(originFilter, CsrfFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/gatewayRoutes")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/*/foundation")
                        .permitAll()
                        .requestMatchers("/bff/login", "/login/oauth2/code/keycloak")
                        .permitAll()
                        .requestMatchers("/bff/csrf")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2Login(login -> login.authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestRepository(authorizationRequestRepository))
                        .authorizedClientRepository(authorizedClientRepository)
                        .successHandler(successHandler)
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.getWriter()
                                    .write(
                                            "{\"type\":\"urn:commerce:problem:authentication-failed\",\"title\":\"Authentication failed\",\"status\":401}");
                        }))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/problem+json");
                    response.getWriter()
                            .write(
                                    "{\"type\":\"urn:commerce:problem:missing-session\",\"title\":\"Authentication required\",\"status\":401}");
                }))
                .build();
    }
}
