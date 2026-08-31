package com.commerce.identityaccess.customeraccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnedResourceNotFoundException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnershipRequiredException;
import com.commerce.identityaccess.customeraccount.models.ActiveCustomerPrincipal;
import com.commerce.identityaccess.customeraccount.services.CustomerAccountService;
import com.commerce.identityaccess.customeraccount.services.PrincipalAccessService;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrincipalAccessServiceTest {
    private static final UUID ACCOUNT_ID = new UUID(0x4d3b40a8cf874c50L, 0xa34c97ba08e92121L);
    private static final String ISSUER = "https://id.example/realms/commerce";
    private static final String SUBJECT = "customer-a";

    @Mock
    private CustomerAccountService customerAccountService;

    @Mock
    private CustomerAccountAuditLogger auditLogger;

    @Test
    void derivesOwnershipOnlyFromTheAuthenticatedCustomerPrincipal() {
        ActiveCustomerPrincipal expected = new ActiveCustomerPrincipal(ACCOUNT_ID, ISSUER, SUBJECT, 3);
        when(customerAccountService.requireActive(ACCOUNT_ID, ISSUER, SUBJECT, 3))
                .thenReturn(expected);

        ActiveCustomerPrincipal actual = service().requireActiveCustomer(customerPrincipal());

        assertEquals(expected, actual);
        verify(customerAccountService).requireActive(ACCOUNT_ID, ISSUER, SUBJECT, 3);
    }

    @Test
    void maintainerCannotBeConvertedIntoCustomerOwnershipAuthority() {
        PrincipalContext maintainer = new PrincipalContext(
                UUID.randomUUID(),
                ISSUER,
                "maintainer",
                PrincipalKind.CATALOG_MAINTAINER,
                null,
                null,
                Instant.EPOCH,
                Set.of("ROLE_CATALOG_MAINTAINER"));

        assertThrows(CustomerOwnershipRequiredException.class, () -> service().requireActiveCustomer(maintainer));
    }

    @Test
    void aLegacyOrForgedCustomerPrincipalWithoutAccountCoordinatesFailsAsMissingSession() {
        PrincipalContext incomplete = new PrincipalContext(
                UUID.randomUUID(),
                ISSUER,
                SUBJECT,
                PrincipalKind.CUSTOMER,
                null,
                null,
                Instant.EPOCH,
                Set.of("ROLE_CUSTOMER"));

        assertThrows(MissingSessionException.class, () -> service().requireActiveCustomer(incomplete));
    }

    @Test
    void missingAndCrossOwnerRepositoryResultsShareTheSameNotFoundOutcome() {
        assertThrows(
                CustomerOwnedResourceNotFoundException.class, () -> service().requireOwned(Optional.empty()));
    }

    private PrincipalAccessService service() {
        return new PrincipalAccessService(customerAccountService, auditLogger);
    }

    private PrincipalContext customerPrincipal() {
        return new PrincipalContext(
                UUID.randomUUID(),
                ISSUER,
                SUBJECT,
                PrincipalKind.CUSTOMER,
                ACCOUNT_ID,
                3L,
                Instant.EPOCH,
                Set.of("ROLE_CUSTOMER"));
    }
}
