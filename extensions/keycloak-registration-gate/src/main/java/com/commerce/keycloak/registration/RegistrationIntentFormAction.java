package com.commerce.keycloak.registration;

import jakarta.ws.rs.core.MultivaluedMap;
import java.time.Clock;
import java.util.List;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;

public final class RegistrationIntentFormAction implements FormAction {
    static final String EXPECTED_CLIENT_ID = "identity-access-bff";
    private static final String SINGLE_USE_PREFIX = "commerce-registration-intent:";
    private static final String GENERIC_REJECTION = "Registration is unavailable.";

    private final RegistrationIntentVerifier verifier;
    private final Clock clock;

    RegistrationIntentFormAction(RegistrationIntentVerifier verifier, Clock clock) {
        this.verifier = verifier;
        this.clock = clock;
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {}

    @Override
    public void validate(ValidationContext context) {
        String intent = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM);
        String prompt = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.PROMPT_PARAM);
        String clientId = context.getAuthenticationSession().getClient().getClientId();
        boolean accepted = intent != null
                && verifier.validateAndConsume(
                        intent,
                        clientId,
                        prompt,
                        clock.instant(),
                        (jti, lifespanSeconds) -> context.getSession()
                                .singleUseObjects()
                                .putIfAbsent(SINGLE_USE_PREFIX + jti, lifespanSeconds));
        if (accepted) {
            context.success();
            return;
        }
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        context.validationError(formData, List.of(new FormMessage(null, GENERIC_REJECTION)));
    }

    @Override
    public void success(FormContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            throw new IllegalStateException("Registration completed without a user");
        }
        RoleModel customerRole = context.getRealm().getRole("CUSTOMER");
        if (customerRole == null) {
            throw new IllegalStateException("CUSTOMER role is not configured");
        }
        user.grantRole(customerRole);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void close() {}
}
