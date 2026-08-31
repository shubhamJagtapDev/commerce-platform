CREATE TABLE customer_account (
    account_id UUID PRIMARY KEY,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETING', 'DELETED')),
    security_epoch BIGINT NOT NULL DEFAULT 0 CHECK (security_epoch >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT customer_account_principal_unique UNIQUE (issuer, subject)
);

CREATE INDEX customer_account_active_principal_idx
    ON customer_account (issuer, subject, security_epoch)
    WHERE status = 'ACTIVE';

-- Gate 3 customer sessions cannot be silently upgraded because they did not capture an account or epoch.
DELETE FROM bff_session WHERE principal_kind = 'CUSTOMER';

ALTER TABLE auth_transaction DROP CONSTRAINT auth_transaction_flow_kind_check;
ALTER TABLE auth_transaction
    ALTER COLUMN flow_kind TYPE VARCHAR(32);
ALTER TABLE auth_transaction
    ADD CONSTRAINT auth_transaction_flow_kind_check
    CHECK (flow_kind IN ('LOGIN', 'CUSTOMER_REGISTRATION'));

ALTER TABLE bff_session
    ADD COLUMN account_id UUID REFERENCES customer_account (account_id),
    ADD COLUMN security_epoch BIGINT;

ALTER TABLE bff_session
    ADD CONSTRAINT bff_session_customer_account_check CHECK (
        (principal_kind = 'CUSTOMER' AND account_id IS NOT NULL AND security_epoch IS NOT NULL)
        OR
        (principal_kind = 'CATALOG_MAINTAINER' AND account_id IS NULL AND security_epoch IS NULL)
    );

CREATE INDEX bff_session_customer_account_idx
    ON bff_session (account_id, security_epoch)
    WHERE principal_kind = 'CUSTOMER' AND status = 'ACTIVE';
