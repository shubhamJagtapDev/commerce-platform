CREATE TABLE catalog_foundation_marker (
    marker_name varchar(80) PRIMARY KEY,
    installed_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE catalog_foundation_marker IS
    'Catalog migration ownership marker; removed when the first domain migration supersedes it.';
