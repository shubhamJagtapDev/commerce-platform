CREATE TABLE catalog_maintainer_grant (
    id UUID PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_catalog_maintainer_subject UNIQUE (issuer, subject),
    CONSTRAINT ck_catalog_grant_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_catalog_grant_version CHECK (version >= 0),
    CONSTRAINT ck_catalog_grant_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE TABLE catalog_authorization_probe (
    id UUID PRIMARY KEY,
    grant_id UUID NOT NULL REFERENCES catalog_maintainer_grant (id),
    version BIGINT NOT NULL DEFAULT 0,
    committed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_catalog_authorization_probe_version CHECK (version >= 0)
);

CREATE TABLE catalog_command_idempotency (
    id UUID PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    operation_code VARCHAR(64) NOT NULL,
    key_hash BYTEA NOT NULL,
    request_fingerprint BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_probe_id UUID REFERENCES catalog_authorization_probe (id),
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_catalog_command_idempotency
        UNIQUE (issuer, subject, operation_code, key_hash),
    CONSTRAINT ck_catalog_command_idempotency_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_catalog_command_idempotency_result CHECK (
        (status = 'IN_PROGRESS' AND result_probe_id IS NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND result_probe_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_catalog_command_idempotency_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_catalog_command_idempotency_expiry
    ON catalog_command_idempotency (expires_at, id);
