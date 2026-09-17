CREATE DOMAIN catalog_inr_amount AS NUMERIC
    CONSTRAINT ck_catalog_inr_amount
        CHECK (VALUE >= 0.01 AND VALUE <= 999999999.99 AND scale(VALUE) <= 2);

CREATE TYPE catalog_product_state AS ENUM (
    'DRAFT',
    'PUBLISHED',
    'WITHDRAWN'
);

CREATE TABLE catalog_main_category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT ck_catalog_main_category_name CHECK (btrim(name) <> '')
);

CREATE TABLE catalog_subcategory (
    id UUID PRIMARY KEY,
    main_category_id UUID NOT NULL REFERENCES catalog_main_category (id),
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_catalog_subcategory_main_id UNIQUE (main_category_id, id),
    CONSTRAINT ck_catalog_subcategory_name CHECK (btrim(name) <> '')
);

CREATE TABLE catalog_product (
    id UUID PRIMARY KEY,
    state catalog_product_state NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    last_command_id UUID NOT NULL,
    current_publication_id UUID,
    first_published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_catalog_product_version CHECK (version >= 0),
    CONSTRAINT ck_catalog_product_lifecycle_shape CHECK (
        (state = 'DRAFT' AND current_publication_id IS NULL AND first_published_at IS NULL)
        OR (
            state IN ('PUBLISHED', 'WITHDRAWN')
            AND current_publication_id IS NOT NULL
            AND first_published_at IS NOT NULL
        )
    )
);

CREATE TABLE catalog_variant (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES catalog_product (id),
    first_publication_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_catalog_variant_product_id UNIQUE (product_id, id)
);

CREATE TABLE catalog_product_draft (
    product_id UUID PRIMARY KEY REFERENCES catalog_product (id),
    name VARCHAR(200),
    short_description VARCHAR(2000),
    main_category_id UUID NOT NULL REFERENCES catalog_main_category (id),
    subcategory_id UUID,
    currency VARCHAR(3) NOT NULL,
    image_urls JSONB NOT NULL,
    long_description JSONB,
    option_1_name VARCHAR(50),
    option_2_name VARCHAR(50),
    CONSTRAINT fk_catalog_product_draft_subcategory
        FOREIGN KEY (main_category_id, subcategory_id)
        REFERENCES catalog_subcategory (main_category_id, id),
    CONSTRAINT ck_catalog_product_draft_name CHECK (name IS NULL OR btrim(name) <> ''),
    CONSTRAINT ck_catalog_product_draft_short_description
        CHECK (short_description IS NULL OR btrim(short_description) <> ''),
    CONSTRAINT ck_catalog_product_draft_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_catalog_product_draft_image_urls CHECK (
        jsonb_typeof(image_urls) = 'array'
        AND jsonb_array_length(image_urls) <= 10
    ),
    CONSTRAINT ck_catalog_product_draft_long_description CHECK (
        long_description IS NULL
        OR (
            jsonb_typeof(long_description) = 'object'
            AND octet_length(long_description::TEXT) <= 32768
        )
    ),
    CONSTRAINT ck_catalog_product_draft_option_order
        CHECK (option_2_name IS NULL OR option_1_name IS NOT NULL),
    CONSTRAINT ck_catalog_product_draft_option_1_name
        CHECK (option_1_name IS NULL OR btrim(option_1_name) <> ''),
    CONSTRAINT ck_catalog_product_draft_option_2_name
        CHECK (option_2_name IS NULL OR btrim(option_2_name) <> ''),
    CONSTRAINT ck_catalog_product_draft_distinct_options
        CHECK (option_1_name IS NULL OR option_2_name IS NULL OR option_1_name <> option_2_name)
);

CREATE TABLE catalog_draft_variant (
    product_id UUID NOT NULL REFERENCES catalog_product_draft (product_id),
    variant_id UUID NOT NULL,
    option_1_value VARCHAR(50),
    option_2_value VARCHAR(50),
    amount catalog_inr_amount,
    sellable BOOLEAN NOT NULL,
    PRIMARY KEY (product_id, variant_id),
    CONSTRAINT fk_catalog_draft_variant_identity
        FOREIGN KEY (product_id, variant_id)
        REFERENCES catalog_variant (product_id, id),
    CONSTRAINT uq_catalog_draft_variant_selection
        UNIQUE NULLS NOT DISTINCT (product_id, option_1_value, option_2_value),
    CONSTRAINT ck_catalog_draft_variant_option_order
        CHECK (option_2_value IS NULL OR option_1_value IS NOT NULL),
    CONSTRAINT ck_catalog_draft_variant_option_1_value
        CHECK (option_1_value IS NULL OR btrim(option_1_value) <> ''),
    CONSTRAINT ck_catalog_draft_variant_option_2_value
        CHECK (option_2_value IS NULL OR btrim(option_2_value) <> '')
);

CREATE TABLE catalog_publication (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES catalog_product (id),
    product_version BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(2000) NOT NULL,
    main_category_id UUID NOT NULL REFERENCES catalog_main_category (id),
    subcategory_id UUID,
    currency VARCHAR(3) NOT NULL,
    image_urls JSONB NOT NULL,
    long_description JSONB,
    option_1_name VARCHAR(50),
    option_2_name VARCHAR(50),
    sellable_count SMALLINT NOT NULL,
    sealed BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_catalog_publication_product_id UNIQUE (product_id, id),
    CONSTRAINT uq_catalog_publication_product_version UNIQUE (product_id, product_version),
    CONSTRAINT fk_catalog_publication_subcategory
        FOREIGN KEY (main_category_id, subcategory_id)
        REFERENCES catalog_subcategory (main_category_id, id),
    CONSTRAINT ck_catalog_publication_product_version CHECK (product_version > 0),
    CONSTRAINT ck_catalog_publication_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_catalog_publication_short_description CHECK (btrim(short_description) <> ''),
    CONSTRAINT ck_catalog_publication_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_catalog_publication_image_urls CHECK (
        jsonb_typeof(image_urls) = 'array'
        AND jsonb_array_length(image_urls) <= 10
    ),
    CONSTRAINT ck_catalog_publication_long_description CHECK (
        long_description IS NULL
        OR (
            jsonb_typeof(long_description) = 'object'
            AND octet_length(long_description::TEXT) <= 32768
        )
    ),
    CONSTRAINT ck_catalog_publication_option_order
        CHECK (option_2_name IS NULL OR option_1_name IS NOT NULL),
    CONSTRAINT ck_catalog_publication_option_1_name
        CHECK (option_1_name IS NULL OR btrim(option_1_name) <> ''),
    CONSTRAINT ck_catalog_publication_option_2_name
        CHECK (option_2_name IS NULL OR btrim(option_2_name) <> ''),
    CONSTRAINT ck_catalog_publication_distinct_options
        CHECK (option_1_name IS NULL OR option_2_name IS NULL OR option_1_name <> option_2_name),
    CONSTRAINT ck_catalog_publication_sellable_count CHECK (sellable_count BETWEEN 1 AND 100)
);

CREATE TABLE catalog_publication_variant (
    publication_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    option_1_value VARCHAR(50),
    option_2_value VARCHAR(50),
    amount catalog_inr_amount,
    sellable BOOLEAN NOT NULL,
    PRIMARY KEY (publication_id, variant_id),
    CONSTRAINT fk_catalog_publication_variant_publication
        FOREIGN KEY (product_id, publication_id)
        REFERENCES catalog_publication (product_id, id),
    CONSTRAINT fk_catalog_publication_variant_identity
        FOREIGN KEY (product_id, variant_id)
        REFERENCES catalog_variant (product_id, id),
    CONSTRAINT uq_catalog_publication_variant_selection
        UNIQUE NULLS NOT DISTINCT (publication_id, option_1_value, option_2_value),
    CONSTRAINT ck_catalog_publication_variant_sellable_price
        CHECK (NOT sellable OR amount IS NOT NULL),
    CONSTRAINT ck_catalog_publication_variant_option_order
        CHECK (option_2_value IS NULL OR option_1_value IS NOT NULL),
    CONSTRAINT ck_catalog_publication_variant_option_1_value
        CHECK (option_1_value IS NULL OR btrim(option_1_value) <> ''),
    CONSTRAINT ck_catalog_publication_variant_option_2_value
        CHECK (option_2_value IS NULL OR btrim(option_2_value) <> '')
);

ALTER TABLE catalog_product
    ADD CONSTRAINT fk_catalog_product_current_publication
    FOREIGN KEY (id, current_publication_id)
    REFERENCES catalog_publication (product_id, id);

ALTER TABLE catalog_variant
    ADD CONSTRAINT fk_catalog_variant_first_publication
    FOREIGN KEY (product_id, first_publication_id)
    REFERENCES catalog_publication (product_id, id);

CREATE TABLE catalog_public_listing (
    product_id UUID PRIMARY KEY REFERENCES catalog_product (id),
    publication_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(2000) NOT NULL,
    main_category_id UUID NOT NULL REFERENCES catalog_main_category (id),
    subcategory_id UUID,
    currency VARCHAR(3) NOT NULL,
    representative_amount catalog_inr_amount NOT NULL,
    price_from BOOLEAN NOT NULL,
    sellable_count SMALLINT NOT NULL,
    CONSTRAINT fk_catalog_public_listing_publication
        FOREIGN KEY (product_id, publication_id)
        REFERENCES catalog_publication (product_id, id),
    CONSTRAINT fk_catalog_public_listing_subcategory
        FOREIGN KEY (main_category_id, subcategory_id)
        REFERENCES catalog_subcategory (main_category_id, id),
    CONSTRAINT ck_catalog_public_listing_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_catalog_public_listing_short_description CHECK (btrim(short_description) <> ''),
    CONSTRAINT ck_catalog_public_listing_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_catalog_public_listing_sellable_count CHECK (sellable_count BETWEEN 1 AND 100)
);

CREATE TABLE catalog_lifecycle_command (
    id UUID PRIMARY KEY,
    grant_id UUID NOT NULL,
    operation_code VARCHAR(32) NOT NULL,
    hash_key_id VARCHAR(64) NOT NULL,
    key_hash BYTEA NOT NULL,
    request_fingerprint BYTEA NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    result_version BIGINT NOT NULL,
    result_state VARCHAR(16) NOT NULL,
    publication_id UUID,
    http_status SMALLINT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_catalog_lifecycle_command_replay_scope
        UNIQUE (grant_id, operation_code, key_hash),
    CONSTRAINT ck_catalog_lifecycle_command_key_hash CHECK (octet_length(key_hash) = 32),
    CONSTRAINT ck_catalog_lifecycle_command_fingerprint CHECK (octet_length(request_fingerprint) = 32),
    CONSTRAINT ck_catalog_lifecycle_command_result_version CHECK (result_version >= 0),
    CONSTRAINT ck_catalog_lifecycle_command_result_state
        CHECK (result_state IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN', 'DISCARDED')),
    CONSTRAINT ck_catalog_lifecycle_command_http_status CHECK (http_status IN (200, 201)),
    CONSTRAINT ck_catalog_lifecycle_command_expiry CHECK (expires_at > applied_at),
    CONSTRAINT ck_catalog_lifecycle_command_operation CHECK (
        operation_code IN (
            'CREATE_DRAFT',
            'REPLACE_CANDIDATE',
            'PUBLISH',
            'REPUBLISH',
            'WITHDRAW_PRODUCT',
            'WITHDRAW_VARIANT',
            'REACTIVATE',
            'DISCARD'
        )
    )
);

CREATE TABLE catalog_lifecycle_audit (
    id UUID PRIMARY KEY,
    command_id UUID NOT NULL UNIQUE,
    actor_key_id VARCHAR(64) NOT NULL,
    actor_hash BYTEA NOT NULL,
    operation_code VARCHAR(32) NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    before_state VARCHAR(16),
    before_version BIGINT,
    after_state VARCHAR(16) NOT NULL,
    after_version BIGINT NOT NULL,
    publication_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_catalog_lifecycle_audit_actor_hash CHECK (octet_length(actor_hash) = 32),
    CONSTRAINT ck_catalog_lifecycle_audit_before_state
        CHECK (before_state IS NULL OR before_state IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN')),
    CONSTRAINT ck_catalog_lifecycle_audit_before_version
        CHECK (before_version IS NULL OR before_version >= 0),
    CONSTRAINT ck_catalog_lifecycle_audit_after_state
        CHECK (after_state IN ('DRAFT', 'PUBLISHED', 'WITHDRAWN', 'DISCARDED')),
    CONSTRAINT ck_catalog_lifecycle_audit_after_version CHECK (after_version >= 0),
    CONSTRAINT ck_catalog_lifecycle_audit_operation CHECK (
        operation_code IN (
            'CREATE_DRAFT',
            'REPLACE_CANDIDATE',
            'PUBLISH',
            'REPUBLISH',
            'WITHDRAW_PRODUCT',
            'WITHDRAW_VARIANT',
            'REACTIVATE',
            'DISCARD'
        )
    )
);

CREATE INDEX idx_catalog_product_state_id ON catalog_product (state, id);
CREATE INDEX idx_catalog_publication_variant_identity
    ON catalog_publication_variant (product_id, variant_id);
CREATE INDEX idx_catalog_lifecycle_command_expiry
    ON catalog_lifecycle_command (expires_at, id);
CREATE INDEX idx_catalog_lifecycle_audit_time
    ON catalog_lifecycle_audit (recorded_at, id);
CREATE INDEX idx_catalog_lifecycle_audit_product_time
    ON catalog_lifecycle_audit (product_id, recorded_at, id);
