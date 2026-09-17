package com.commerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class CatalogProductLifecycleMigrationTest {
    private static final UUID MAIN_CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBCATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void resetCatalogRows() throws SQLException {
        try (Connection connection = openConnection()) {
            connection.createStatement().execute("""
                            truncate table
                                catalog_lifecycle_audit,
                                catalog_lifecycle_command,
                                catalog_public_listing,
                                catalog_publication_variant,
                                catalog_publication,
                                catalog_draft_variant,
                                catalog_product_draft,
                                catalog_variant,
                                catalog_product,
                                catalog_subcategory,
                                catalog_main_category
                            cascade
                            """);
        }
    }

    @Test
    void flywayAppliesLifecycleSchemaThroughNextAdditiveVersions() throws SQLException {
        try (Connection connection = openConnection();
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery("select version from flyway_schema_history order by version")) {
            List<String> versions = new ArrayList<>();
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }

            assertThat(versions).containsExactly("001", "002", "003", "004", "005");
        }
    }

    @Test
    void inrAmountRejectsValuesThatWouldNeedRoundingBeforeValidation() {
        assertCommitFails(connection -> {
            seedCategory(connection);
            insertValidDraftProduct(
                    connection,
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    new BigDecimal("10.001"));
        });
    }

    @Test
    void validDraftCanCommitWithoutPublicationOrListing() throws SQLException {
        UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        inTransaction(connection -> {
            seedCategory(connection);
            insertValidDraftProduct(
                    connection, productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), BigDecimal.TEN);
        });

        try (Connection connection = openConnection();
                var statement =
                        connection.prepareStatement("select state, version from catalog_product where id = ?")) {
            statement.setObject(1, productId);

            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("state")).isEqualTo("DRAFT");
                assertThat(resultSet.getLong("version")).isZero();
            }
        }
    }

    @Test
    void crossProductDraftVariantReferenceFails() {
        assertCommitFails(connection -> {
            seedCategory(connection);
            UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            insertProductRoot(connection, productId);
            insertDraft(connection, productId);
            insertVariant(connection, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), UUID.randomUUID());

            try (var statement = connection.prepareStatement("""
                    insert into catalog_draft_variant (product_id, variant_id, amount, sellable)
                    values (?, ?, 10.00, true)
                    """)) {
                statement.setObject(1, productId);
                statement.setObject(2, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
                statement.executeUpdate();
            }
        });
    }

    @Test
    void unsealedPublicationCannotCommit() {
        assertCommitFails(connection -> {
            UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID variantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            seedCategory(connection);
            insertValidDraftProduct(connection, productId, variantId, BigDecimal.TEN);
            insertPublicationHeader(
                    connection, productId, UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), 1, 1);
        });
    }

    @Test
    void sealedPublicationRejectsLaterChildInsert() throws SQLException {
        UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID publicationId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        inTransaction(connection -> {
            seedCategory(connection);
            insertValidPublishedProduct(
                    connection, productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), publicationId);
        });

        assertThatThrownBy(() -> inTransaction(connection -> {
                    UUID extraVariantId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
                    insertVariant(connection, extraVariantId, productId);
                    try (var statement = connection.prepareStatement("""
                            insert into catalog_publication_variant (
                                publication_id, product_id, variant_id, amount, sellable)
                            values (?, ?, ?, 12.00, true)
                            """)) {
                        statement.setObject(1, publicationId);
                        statement.setObject(2, productId);
                        statement.setObject(3, extraVariantId);
                        statement.executeUpdate();
                    }
                }))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void publishedProductRequiresEquivalentListingAtCommit() {
        assertCommitFails(connection -> {
            UUID productId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID publicationId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
            seedCategory(connection);
            insertValidPublishedProductWithoutListing(
                    connection, productId, UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), publicationId);
        });
    }

    @Test
    void publishedProductWithEquivalentListingCommits() throws SQLException {
        inTransaction(connection -> {
            seedCategory(connection);
            insertValidPublishedProduct(
                    connection,
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        });
    }

    @Test
    void categoryMainAndSubcategoryIdSetsCannotIntersect() {
        assertCommitFails(connection -> {
            UUID categoryId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            try (var main = connection.prepareStatement("insert into catalog_main_category (id, name) values (?, ?)")) {
                main.setObject(1, categoryId);
                main.setString(2, "Main");
                main.executeUpdate();
            }
            try (var sub = connection.prepareStatement(
                    "insert into catalog_subcategory (id, main_category_id, name) values (?, ?, ?)")) {
                sub.setObject(1, categoryId);
                sub.setObject(2, categoryId);
                sub.setString(3, "Sub");
                sub.executeUpdate();
            }
        });
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void inTransaction(SqlWork work) throws SQLException {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void assertCommitFails(SqlWork work) {
        assertThatThrownBy(() -> inTransaction(work)).isInstanceOf(SQLException.class);
    }

    private static void seedCategory(Connection connection) throws SQLException {
        try (var main = connection.prepareStatement("insert into catalog_main_category (id, name) values (?, ?)")) {
            main.setObject(1, MAIN_CATEGORY_ID);
            main.setString(2, "Test Main");
            main.executeUpdate();
        }
        try (var sub = connection.prepareStatement(
                "insert into catalog_subcategory (id, main_category_id, name) values (?, ?, ?)")) {
            sub.setObject(1, SUBCATEGORY_ID);
            sub.setObject(2, MAIN_CATEGORY_ID);
            sub.setString(3, "Test Sub");
            sub.executeUpdate();
        }
    }

    private static void insertValidDraftProduct(
            Connection connection, UUID productId, UUID variantId, BigDecimal amount) throws SQLException {
        insertProductRoot(connection, productId);
        insertVariant(connection, variantId, productId);
        insertDraft(connection, productId);
        try (var statement = connection.prepareStatement("""
                insert into catalog_draft_variant (product_id, variant_id, amount, sellable)
                values (?, ?, ?, true)
                """)) {
            statement.setObject(1, productId);
            statement.setObject(2, variantId);
            statement.setBigDecimal(3, amount);
            statement.executeUpdate();
        }
    }

    private static void insertProductRoot(Connection connection, UUID productId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into catalog_product (
                    id, last_command_id, created_at, updated_at)
                values (?, ?, ?, ?)
                """)) {
            statement.setObject(1, productId);
            statement.setObject(2, UUID.randomUUID());
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static void insertVariant(Connection connection, UUID variantId, UUID productId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into catalog_variant (id, product_id, created_at)
                values (?, ?, ?)
                """)) {
            statement.setObject(1, variantId);
            statement.setObject(2, productId);
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    private static void insertDraft(Connection connection, UUID productId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into catalog_product_draft (
                    product_id, name, short_description, main_category_id, subcategory_id,
                    currency, image_urls, long_description)
                values (?, 'Product', 'Short description', ?, ?, 'INR', '[]'::jsonb, '{}'::jsonb)
                """)) {
            statement.setObject(1, productId);
            statement.setObject(2, MAIN_CATEGORY_ID);
            statement.setObject(3, SUBCATEGORY_ID);
            statement.executeUpdate();
        }
    }

    private static void insertValidPublishedProduct(
            Connection connection, UUID productId, UUID variantId, UUID publicationId) throws SQLException {
        insertValidPublishedProductWithoutListing(connection, productId, variantId, publicationId);
        try (var statement = connection.prepareStatement("""
                insert into catalog_public_listing (
                    product_id, publication_id, name, short_description, main_category_id, subcategory_id,
                    currency, representative_amount, price_from, sellable_count)
                values (?, ?, 'Product', 'Short description', ?, ?, 'INR', 10.00, false, 1)
                """)) {
            statement.setObject(1, productId);
            statement.setObject(2, publicationId);
            statement.setObject(3, MAIN_CATEGORY_ID);
            statement.setObject(4, SUBCATEGORY_ID);
            statement.executeUpdate();
        }
    }

    private static void insertValidPublishedProductWithoutListing(
            Connection connection, UUID productId, UUID variantId, UUID publicationId) throws SQLException {
        insertValidDraftProduct(connection, productId, variantId, BigDecimal.TEN);
        insertPublicationHeader(connection, productId, publicationId, 1, 1);
        try (var statement = connection.prepareStatement("""
                insert into catalog_publication_variant (
                    publication_id, product_id, variant_id, amount, sellable)
                values (?, ?, ?, 10.00, true)
                """)) {
            statement.setObject(1, publicationId);
            statement.setObject(2, productId);
            statement.setObject(3, variantId);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("update catalog_publication set sealed = true where id = ?")) {
            statement.setObject(1, publicationId);
            statement.executeUpdate();
        }
        try (var statement =
                connection.prepareStatement("update catalog_variant set first_publication_id = ? where id = ?")) {
            statement.setObject(1, publicationId);
            statement.setObject(2, variantId);
            statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("""
                update catalog_product
                set state = 'PUBLISHED',
                    version = 1,
                    current_publication_id = ?,
                    first_published_at = ?,
                    updated_at = ?,
                    last_command_id = ?
                where id = ?
                """)) {
            statement.setObject(1, publicationId);
            statement.setTimestamp(2, Timestamp.from(NOW));
            statement.setTimestamp(3, Timestamp.from(NOW));
            statement.setObject(4, UUID.randomUUID());
            statement.setObject(5, productId);
            statement.executeUpdate();
        }
    }

    private static void insertPublicationHeader(
            Connection connection, UUID productId, UUID publicationId, int productVersion, int sellableCount)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into catalog_publication (
                    id, product_id, product_version, name, short_description, main_category_id, subcategory_id,
                    currency, image_urls, long_description, sellable_count, applied_at)
                values (?, ?, ?, 'Product', 'Short description', ?, ?, 'INR', '[]'::jsonb, '{}'::jsonb, ?, ?)
                """)) {
            statement.setObject(1, publicationId);
            statement.setObject(2, productId);
            statement.setInt(3, productVersion);
            statement.setObject(4, MAIN_CATEGORY_ID);
            statement.setObject(5, SUBCATEGORY_ID);
            statement.setInt(6, sellableCount);
            statement.setTimestamp(7, Timestamp.from(NOW));
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
