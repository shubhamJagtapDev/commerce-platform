package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.stereotype.Component;

/** Owns the fixed OIDC login request so initial redirects and callback reconstruction cannot drift. */
@Component
public final class OidcAuthorizationRequestFactory {
    private static final String REGISTRATION_ID = "keycloak";
    private static final Set<String> SCOPES = Set.of("openid", "roles");

    private final AuthProperties properties;
    private final VersionedCryptoService cryptoService;

    public OidcAuthorizationRequestFactory(AuthProperties properties, VersionedCryptoService cryptoService) {
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    public OAuth2AuthorizationRequest createLoginRequest() {
        String state = cryptoService.randomUrlValue();
        String nonce = cryptoService.randomUrlValue();
        String verifier = cryptoService.randomUrlValue();
        return create(state, nonce, verifier);
    }

    public OAuth2AuthorizationRequest restore(String state, String nonce, String verifier) {
        return create(state, nonce, verifier);
    }

    private OAuth2AuthorizationRequest create(String state, String nonce, String verifier) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(properties.publicIssuer() + "/protocol/openid-connect/auth")
                .clientId(properties.clientId())
                .redirectUri(properties.publicOrigin() + "/login/oauth2/code/keycloak")
                .scopes(SCOPES)
                .state(state)
                .attributes(attributes -> {
                    attributes.put(OAuth2ParameterNames.REGISTRATION_ID, REGISTRATION_ID);
                    attributes.put(OidcParameterNames.NONCE, nonce);
                    attributes.put(PkceParameterNames.CODE_VERIFIER, verifier);
                })
                .additionalParameters(Map.of(
                        OidcParameterNames.NONCE,
                        cryptoService.sha256Url(nonce),
                        PkceParameterNames.CODE_CHALLENGE,
                        cryptoService.sha256Url(verifier),
                        PkceParameterNames.CODE_CHALLENGE_METHOD,
                        "S256"))
                .build();
    }
}
