package com.commerce.identityaccess.customeraccount.services;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.customeraccount.exceptions.CustomerAccountBindingException;
import com.commerce.identityaccess.customeraccount.models.ActiveCustomerPrincipal;
import com.commerce.identityaccess.customeraccount.models.CustomerAccount;
import com.commerce.identityaccess.customeraccount.models.CustomerAccountEntity;
import com.commerce.identityaccess.customeraccount.models.CustomerAccountStatus;
import com.commerce.identityaccess.customeraccount.repositories.CustomerAccountRepository;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger;
import com.commerce.identityaccess.customeraccount.utils.CustomerAccountAuditLogger.OwnershipDenial;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAccountService {
    private final CustomerAccountRepository repository;
    private final Clock clock;
    private final CustomerAccountAuditLogger auditLogger;

    public CustomerAccountService(
            CustomerAccountRepository repository, Clock clock, CustomerAccountAuditLogger auditLogger) {
        this.repository = repository;
        this.clock = clock;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public CustomerAccount establish(String issuer, String subject) {
        Instant now = clock.instant();
        repository.insertBindingIfAbsent(UUID.randomUUID(), issuer, subject, now);
        CustomerAccount account = repository
                .findByIssuerAndSubject(issuer, subject)
                .map(CustomerAccountService::toAccount)
                .orElseThrow(IllegalStateException::new);
        if (account.status() != CustomerAccountStatus.ACTIVE) {
            auditLogger.accountBindingRejected();
            throw new CustomerAccountBindingException();
        }
        auditLogger.accountBindingAccepted();
        return account;
    }

    @Transactional(readOnly = true)
    public ActiveCustomerPrincipal requireActive(UUID accountId, String issuer, String subject, long securityEpoch) {
        CustomerAccount account = repository
                .findActiveOwnedAccount(accountId, issuer, subject, securityEpoch)
                .map(CustomerAccountService::toAccount)
                .orElseThrow(() -> {
                    auditLogger.ownershipRejected(OwnershipDenial.INACTIVE_OR_MISMATCHED_ACCOUNT);
                    return new MissingSessionException();
                });
        return new ActiveCustomerPrincipal(
                account.accountId(), account.issuer(), account.subject(), account.securityEpoch());
    }

    private static CustomerAccount toAccount(CustomerAccountEntity entity) {
        return new CustomerAccount(
                entity.getAccountId(),
                entity.getIssuer(),
                entity.getSubject(),
                entity.getStatus(),
                entity.getSecurityEpoch(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
