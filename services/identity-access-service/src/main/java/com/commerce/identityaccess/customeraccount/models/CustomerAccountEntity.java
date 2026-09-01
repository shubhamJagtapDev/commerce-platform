package com.commerce.identityaccess.customeraccount.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(
        name = "customer_account",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "customer_account_principal_unique",
                        columnNames = {"issuer", "subject"}))
@Getter
public class CustomerAccountEntity {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "issuer", nullable = false, length = 512)
    private String issuer;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CustomerAccountStatus status;

    @Column(name = "security_epoch", nullable = false)
    private long securityEpoch;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerAccountEntity() {}

    public CustomerAccountEntity(UUID accountId, String issuer, String subject, Instant now) {
        this.accountId = accountId;
        this.issuer = issuer;
        this.subject = subject;
        this.status = CustomerAccountStatus.ACTIVE;
        this.securityEpoch = 0;
        this.version = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
