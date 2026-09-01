package com.commerce.identityaccess.customeraccount.repositories;

import com.commerce.identityaccess.customeraccount.models.CustomerAccountEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccountEntity, UUID> {
    @Modifying
    @Query(value = """
                    insert into customer_account (
                        account_id, issuer, subject, status, security_epoch, version, created_at, updated_at)
                    values (:accountId, :issuer, :subject, 'ACTIVE', 0, 0, :now, :now)
                    on conflict (issuer, subject) do nothing
                    """, nativeQuery = true)
    int insertBindingIfAbsent(
            @Param("accountId") UUID accountId,
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("now") Instant now);

    Optional<CustomerAccountEntity> findByIssuerAndSubject(String issuer, String subject);

    @Query("""
            select account
            from CustomerAccountEntity account
            where account.accountId = :accountId
              and account.issuer = :issuer
              and account.subject = :subject
              and account.status = CustomerAccountStatus.ACTIVE
              and account.securityEpoch = :securityEpoch
            """)
    Optional<CustomerAccountEntity> findActiveOwnedAccount(
            @Param("accountId") UUID accountId,
            @Param("issuer") String issuer,
            @Param("subject") String subject,
            @Param("securityEpoch") long securityEpoch);
}
