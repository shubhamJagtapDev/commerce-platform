package com.commerce.identityaccess.auth.repositories;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.AuthFlowKind;
import com.commerce.identityaccess.auth.services.VersionedCryptoService;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthTransactionStore {
    private final JdbcTemplate jdbcTemplate;
    private final VersionedCryptoService cryptoService;
    private final AuthProperties properties;
    private final Clock clock;

    AuthTransactionStore(
            JdbcTemplate jdbcTemplate, VersionedCryptoService cryptoService, AuthProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.clock = clock;
    }

    void create(String state, String nonce, String verifier, AuthFlowKind flowKind) {
        Instant now = clock.instant();
        VersionedCryptoService.EncryptedValue encryptedNonce =
                cryptoService.encrypt("oidc-transaction-nonce", nonce.getBytes(StandardCharsets.UTF_8));
        VersionedCryptoService.EncryptedValue encryptedVerifier =
                cryptoService.encrypt("oidc-transaction-pkce", verifier.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedODICState = cryptoService.hmac("oidc-state", state.getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update(
                """
                insert into auth_transaction (
                    state_hash, encryption_key_id, nonce_ciphertext, pkce_verifier_ciphertext,
                    flow_kind, return_target, created_at, expires_at)
                values (?, ?, ?, ?, ?, '/bff/csrf', ?, ?)
                """,
                encryptedODICState,
                encryptedNonce.keyId(),
                encryptedNonce.ciphertext(),
                encryptedVerifier.ciphertext(),
                flowKind.name(),
                Timestamp.from(now),
                Timestamp.from(now.plus(properties.transactionTtl())));
    }

    TransactionMaterial findUsable(String state) {
        return find(state, false);
    }

    TransactionMaterial claim(String state) {
        Instant now = clock.instant();
        List<TransactionMaterial> transactions = jdbcTemplate.query(
                """
                update auth_transaction
                set consumed_at = ?
                where state_hash = ? and consumed_at is null and expires_at > ?
                returning encryption_key_id, nonce_ciphertext, pkce_verifier_ciphertext, flow_kind
                """,
                this::mapMaterial,
                Timestamp.from(now),
                cryptoService.hmac("oidc-state", state.getBytes(StandardCharsets.UTF_8)),
                Timestamp.from(now));
        if (transactions.size() != 1) {
            throw new AuthenticationFailureException();
        }
        return transactions.getFirst();
    }

    private TransactionMaterial find(String state, boolean consumed) {
        Instant now = clock.instant();
        List<TransactionMaterial> transactions = jdbcTemplate.query(
                """
                select encryption_key_id, nonce_ciphertext, pkce_verifier_ciphertext, flow_kind
                from auth_transaction
                where state_hash = ? and consumed_at is null and expires_at > ?
                """,
                this::mapMaterial,
                cryptoService.hmac("oidc-state", state.getBytes(StandardCharsets.UTF_8)),
                Timestamp.from(now));
        if (consumed || transactions.size() != 1) {
            throw new AuthenticationFailureException();
        }
        return transactions.getFirst();
    }

    private TransactionMaterial mapMaterial(ResultSet resultSet, int rowNumber) throws SQLException {
        String keyId = resultSet.getString("encryption_key_id");
        byte[] nonce = cryptoService.decrypt("oidc-transaction-nonce", keyId, resultSet.getBytes("nonce_ciphertext"));
        byte[] verifier =
                cryptoService.decrypt("oidc-transaction-pkce", keyId, resultSet.getBytes("pkce_verifier_ciphertext"));
        return new TransactionMaterial(
                new String(nonce, StandardCharsets.UTF_8),
                new String(verifier, StandardCharsets.UTF_8),
                AuthFlowKind.valueOf(resultSet.getString("flow_kind")));
    }

    record TransactionMaterial(String nonce, String verifier, AuthFlowKind flowKind) {}
}
