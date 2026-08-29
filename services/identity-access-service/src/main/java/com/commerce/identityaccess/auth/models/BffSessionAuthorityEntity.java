package com.commerce.identityaccess.auth.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "bff_session_authority")
public class BffSessionAuthorityEntity {
    @Id
    @Column(name = "authority_id", nullable = false)
    private UUID authorityId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Getter
    @Column(name = "authority_code", nullable = false)
    private String authorityCode;

    protected BffSessionAuthorityEntity() {}

    public BffSessionAuthorityEntity(UUID sessionId, String authorityCode) {
        this.authorityId = UUID.randomUUID();
        this.sessionId = sessionId;
        this.authorityCode = authorityCode;
    }
}
