package com.commerce.keycloak.registration;

import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.keycloak.Config;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public final class RegistrationIntentFormActionFactory implements FormActionFactory {
    public static final String PROVIDER_ID = "commerce-registration-intent";
    private static final long MAXIMUM_TTL_SECONDS = 600;
    private @Nullable RegistrationIntentVerifier verifier;

    @Override
    public FormAction create(KeycloakSession session) {
        RegistrationIntentVerifier configuredVerifier = verifier;
        if (configuredVerifier == null) {
            throw new IllegalStateException("Registration intent provider is not initialized");
        }
        return new RegistrationIntentFormAction(configuredVerifier, Clock.systemUTC());
    }

    @Override
    public void init(Config.Scope config) {
        String encodedKey = System.getenv("IDENTITY_REGISTRATION_INTENT_HMAC_KEY");
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("IDENTITY_REGISTRATION_INTENT_HMAC_KEY is required");
        }
        verifier = new RegistrationIntentVerifier(encodedKey, MAXIMUM_TTL_SECONDS);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Commerce registration intent";
    }

    @Override
    public String getReferenceCategory() {
        return "commerce-registration-intent";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return ConfigurableAuthenticatorFactory.REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Requires a signed, short-lived, single-use registration intent issued by Identity Access.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
