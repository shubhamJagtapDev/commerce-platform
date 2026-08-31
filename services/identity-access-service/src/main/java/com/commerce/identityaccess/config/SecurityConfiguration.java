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
import com.commerce.identityaccess.auth.services.SafeOidcAuthenticationFailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain applicationSecurity(
            HttpSecurity http,
            DatabaseAuthorizationRequestRepository authorizationRequestRepository,
            RequestAuthorizedClientRepository authorizedClientRepository,
            OidcBffAuthenticationSuccessHandler successHandler,
            SafeOidcAuthenticationFailureHandler failureHandler,
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
                .csrf(csrf -> csrf.csrfTokenRepository(new SessionBoundCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterBefore(sessionFilter, CsrfFilter.class)
                .addFilterAfter(originFilter, BffSessionAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/gatewayRoutes")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/*/foundation")
                        .permitAll()
                        .requestMatchers("/bff/login", "/bff/register", "/login/oauth2/code/keycloak")
                        .permitAll()
                        .requestMatchers("/bff/csrf")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/catalog/authorization-probes")
                        .access((authentication, context) -> new AuthorizationDecision(context.getRequest()
                                        .getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE)
                                != null))
                        .requestMatchers("/api/v1/catalog/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .oauth2Login(login -> login.authorizationEndpoint(
                                endpoint -> endpoint.authorizationRequestRepository(authorizationRequestRepository))
                        .authorizedClientRepository(authorizedClientRepository)
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/problem+json");
                            response.setHeader("Cache-Control", "no-store");
                            response.getWriter()
                                    .write(
                                            "{\"type\":\"urn:commerce:problem:missing-session\",\"title\":\"Authentication required\",\"status\":401,\"code\":\"AUTHENTICATION_REQUIRED\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean anonymous =
                                    request.getAttribute(BffSessionAuthenticationFilter.RESOLVED_SESSION_ATTRIBUTE)
                                            == null;
                            if (anonymous) {
                                response.setStatus(401);
                                response.setContentType("application/problem+json");
                                response.setHeader("Cache-Control", "no-store");
                                response.getWriter()
                                        .write(
                                                "{\"type\":\"urn:commerce:problem:missing-session\",\"title\":\"Authentication required\",\"status\":401,\"code\":\"AUTHENTICATION_REQUIRED\"}");
                                return;
                            }
                            if (exception instanceof CsrfException) {
                                response.setStatus(403);
                                response.setContentType("application/problem+json");
                                response.setHeader("Cache-Control", "no-store");
                                response.getWriter()
                                        .write(
                                                "{\"type\":\"urn:commerce:problem:csrf-rejected\",\"title\":\"Request rejected\",\"status\":403,\"code\":\"CSRF_REJECTED\"}");
                                return;
                            }
                            response.setStatus(403);
                            response.setContentType("application/problem+json");
                            response.setHeader("Cache-Control", "no-store");
                            response.getWriter()
                                    .write(
                                            "{\"type\":\"urn:commerce:problem:forbidden\",\"title\":\"Forbidden\",\"status\":403,\"code\":\"FORBIDDEN\"}");
                        }))
                .build();
    }
}
