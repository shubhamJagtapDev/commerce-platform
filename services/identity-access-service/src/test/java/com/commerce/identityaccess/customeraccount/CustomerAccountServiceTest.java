package com.commerce.identityaccess.customeraccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerAccountBindingException;
import com.commerce.identityaccess.customeraccount.models.ActiveCustomerPrincipal;
import com.commerce.identityaccess.customeraccount.models.CustomerAccount;
import com.commerce.identityaccess.customeraccount.models.CustomerAccountEntity;
import com.commerce.identityaccess.customeraccount.models.CustomerAccountStatus;
import com.commerce.identityaccess.customeraccount.repositories.CustomerAccountRepository;
import com.commerce.identityaccess.customeraccount.services.CustomerAccountService;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");
    private static final UUID ACCOUNT_ID = new UUID(0x4d3b40a8cf874c50L, 0xa34c97ba08e92121L);
    private static final String ISSUER = "https://id.example/realms/commerce";
    private static final String SUBJECT = "oidc-subject";

    @Mock
    private CustomerAccountRepository repository;

    @Mock
    private CustomerAccountAuditLogger auditLogger;

    @Test
    void establishesTheExactActiveIssuerSubjectBinding() {
        CustomerAccount account = account(CustomerAccountStatus.ACTIVE);
        CustomerAccountEntity entity = entity(account);
        when(repository.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.of(entity));

        CustomerAccount established = service().establish(ISSUER, SUBJECT);

        assertEquals(account, established);
    }

    @Test
    void refusesToIssueASessionForAnInactiveExistingBinding() {
        CustomerAccount account = account(CustomerAccountStatus.DISABLED);
        CustomerAccountEntity entity = entity(account);
        when(repository.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.of(entity));

        assertThrows(CustomerAccountBindingException.class, () -> service().establish(ISSUER, SUBJECT));
    }

    @Test
    void activeResolutionFailsClosedWhenAnyOwnershipCoordinateDoesNotMatch() {
        CustomerAccount account = account(CustomerAccountStatus.ACTIVE);
        when(repository.findActiveOwnedAccount(account.accountId(), ISSUER, SUBJECT, 0))
                .thenReturn(Optional.empty());

        assertThrows(
                MissingSessionException.class, () -> service().requireActive(account.accountId(), ISSUER, SUBJECT, 0));
    }

    @Test
    void activeResolutionReturnsOnlyThePrincipalDerivedOwnershipValue() {
        CustomerAccount account = account(CustomerAccountStatus.ACTIVE);
        CustomerAccountEntity entity = entity(account);
        when(repository.findActiveOwnedAccount(account.accountId(), ISSUER, SUBJECT, 0))
                .thenReturn(Optional.of(entity));

        ActiveCustomerPrincipal principal = service().requireActive(account.accountId(), ISSUER, SUBJECT, 0);

        assertEquals(account.accountId(), principal.accountId());
        assertEquals(SUBJECT, principal.subject());
    }

    private CustomerAccountService service() {
        return new CustomerAccountService(repository, Clock.fixed(NOW, ZoneOffset.UTC), auditLogger);
    }

    private CustomerAccount account(CustomerAccountStatus status) {
        return new CustomerAccount(ACCOUNT_ID, ISSUER, SUBJECT, status, 0, 0, NOW, NOW);
    }

    private CustomerAccountEntity entity(CustomerAccount account) {
        CustomerAccountEntity entity = mock(CustomerAccountEntity.class);
        when(entity.getAccountId()).thenReturn(account.accountId());
        when(entity.getIssuer()).thenReturn(account.issuer());
        when(entity.getSubject()).thenReturn(account.subject());
        when(entity.getStatus()).thenReturn(account.status());
        when(entity.getSecurityEpoch()).thenReturn(account.securityEpoch());
        when(entity.getVersion()).thenReturn(account.version());
        when(entity.getCreatedAt()).thenReturn(account.createdAt());
        when(entity.getUpdatedAt()).thenReturn(account.updatedAt());
        return entity;
    }
}
