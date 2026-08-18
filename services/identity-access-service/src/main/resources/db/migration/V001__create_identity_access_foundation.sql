CREATE TABLE identity_access_foundation_marker (
    marker_name varchar(80) PRIMARY KEY,
    installed_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE identity_access_foundation_marker IS
    'Identity Access migration ownership marker; removed when the first domain migration supersedes it.';
