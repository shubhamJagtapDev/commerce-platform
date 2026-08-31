package com.commerce.identityaccess.customeraccount.services;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnedResourceNotFoundException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerOwnershipRequiredException;
import com.commerce.identityaccess.customeraccount.models.ActiveCustomerPrincipal;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger.OwnershipDenial;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reusable deny-by-default conversion from a session principal to customer ownership authority. */
@Service
public class PrincipalAccessService {
    private final CustomerAccountService customerAccountService;
    private final CustomerAccountAuditLogger auditLogger;

    public PrincipalAccessService(
            CustomerAccountService customerAccountService, CustomerAccountAuditLogger auditLogger) {
        this.customerAccountService = customerAccountService;
        this.auditLogger = auditLogger;
    }

    public ActiveCustomerPrincipal requireActiveCustomer(PrincipalContext principal) {
        if (principal.kind() != PrincipalKind.CUSTOMER) {
            auditLogger.ownershipRejected(OwnershipDenial.WRONG_ACTOR);
            throw new CustomerOwnershipRequiredException();
        }
        UUID accountId = principal.accountId();
        Long securityEpoch = principal.securityEpoch();
        if (accountId == null || securityEpoch == null) {
            auditLogger.ownershipRejected(OwnershipDenial.INCOMPLETE_SESSION);
            throw new MissingSessionException();
        }
        return customerAccountService.requireActive(
                accountId, principal.issuer(), principal.subject(), securityEpoch.longValue());
    }

    /** Preserves non-enumerating 404 semantics after a repository has queried by resource id and owner id together. */
    public <T> T requireOwned(Optional<T> ownedResource) {
        return ownedResource.orElseThrow(() -> {
            auditLogger.ownershipRejected(OwnershipDenial.RESOURCE_NOT_FOUND);
            return new CustomerOwnedResourceNotFoundException();
        });
    }
}
