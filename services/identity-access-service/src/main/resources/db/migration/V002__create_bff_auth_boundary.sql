CREATE TABLE auth_transaction (
    state_hash BYTEA PRIMARY KEY,
    encryption_key_id VARCHAR(64) NOT NULL,
    nonce_ciphertext BYTEA NOT NULL,
    pkce_verifier_ciphertext BYTEA NOT NULL,
    flow_kind VARCHAR(16) NOT NULL CHECK (flow_kind = 'LOGIN'),
    return_target VARCHAR(32) NOT NULL CHECK (return_target = '/bff/csrf'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at)
);

CREATE INDEX auth_transaction_cleanup_idx
    ON auth_transaction (expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE bff_session (
    session_id UUID PRIMARY KEY,
    handle_hash BYTEA NOT NULL UNIQUE,
    csrf_hash BYTEA NOT NULL,
    encryption_key_id VARCHAR(64) NOT NULL,
    token_bundle_ciphertext BYTEA NOT NULL,
    principal_kind VARCHAR(32) NOT NULL CHECK (principal_kind IN ('CUSTOMER', 'CATALOG_MAINTAINER')),
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    oidc_session_id VARCHAR(255),
    authenticated_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status = 'ACTIVE'),
    CHECK (idle_expires_at <= absolute_expires_at),
    CHECK (absolute_expires_at > authenticated_at)
);

CREATE INDEX bff_session_lookup_idx
    ON bff_session (handle_hash, status, idle_expires_at, absolute_expires_at);

CREATE INDEX bff_session_cleanup_idx
    ON bff_session (absolute_expires_at, idle_expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE bff_session_authority (
    authority_id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES bff_session (session_id) ON DELETE CASCADE,
    authority_code VARCHAR(64) NOT NULL,
    CONSTRAINT bff_session_authority_unique UNIQUE (session_id, authority_code)
);

CREATE INDEX bff_session_authority_session_idx ON bff_session_authority (session_id);
