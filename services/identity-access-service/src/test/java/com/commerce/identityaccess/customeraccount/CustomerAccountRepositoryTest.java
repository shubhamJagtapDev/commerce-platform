package com.commerce.identityaccess.customeraccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.models.CreatedBffSession;
import com.commerce.identityaccess.auth.models.OidcTokenBundle;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.models.ValidatedOidcPrincipal;
import com.commerce.identityaccess.auth.services.BffSessionService;
import com.commerce.identityaccess.customeraccount.models.CustomerAccount;
import com.commerce.identityaccess.customeraccount.services.CustomerAccountService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        properties = {
            "IDENTITY_OIDC_CLIENT_SECRET=test-client-secret",
            "IDENTITY_AUTH_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "IDENTITY_AUTH_HMAC_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        })
@Testcontainers(disabledWithoutDocker = true)
class CustomerAccountRepositoryTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private CustomerAccountService customerAccountService;

    @Autowired
    private BffSessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentCallbacksEstablishExactlyOneIssuerSubjectAccount() throws Exception {
        int callbacks = 16;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(callbacks)) {
            List<java.util.concurrent.Future<CustomerAccount>> results = java.util.stream.IntStream.range(0, callbacks)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return customerAccountService.establish(
                                "https://id.example/realms/commerce", "concurrent-subject");
                    }))
                    .toList();

            start.countDown();
            Set<java.util.UUID> accountIds = new java.util.HashSet<>();
            for (var result : results) {
                accountIds.add(result.get().accountId());
            }

            assertEquals(1, accountIds.size());
        }
    }

    @Test
    void disablingAnAccountInvalidatesItsPreviouslyIssuedSession() {
        ValidatedOidcPrincipal principal = new ValidatedOidcPrincipal(
                "http://localhost:8082/realms/commerce",
                "revoked-customer",
                "oidc-session",
                PrincipalKind.CUSTOMER,
                Set.of("ROLE_CUSTOMER"));
        CreatedBffSession session =
                sessionService.create(principal, new OidcTokenBundle("id-token", "access-token", null));
        sessionService.resolve(session.rawHandle());

        jdbcTemplate.update(
                "update customer_account set status = 'DISABLED', security_epoch = security_epoch + 1 where account_id = ?",
                session.principal().accountId());

        assertThrows(MissingSessionException.class, () -> sessionService.resolve(session.rawHandle()));
    }

    @Test
    void maintainerSessionsRemainAccountless() {
        ValidatedOidcPrincipal principal = new ValidatedOidcPrincipal(
                "http://localhost:8082/realms/commerce",
                "catalog-maintainer",
                "maintainer-oidc-session",
                PrincipalKind.CATALOG_MAINTAINER,
                Set.of("ROLE_CATALOG_MAINTAINER"));

        CreatedBffSession session =
                sessionService.create(principal, new OidcTokenBundle("id-token", "access-token", null));

        assertNull(session.principal().accountId());
        assertNull(session.principal().securityEpoch());
    }

    @Test
    void registrationFlowKindFitsThePersistedTransactionContract() {
        Instant now = Instant.parse("2026-08-29T00:00:00Z");

        int inserted = jdbcTemplate.update(
                """
                insert into auth_transaction (
                    state_hash, encryption_key_id, nonce_ciphertext, pkce_verifier_ciphertext,
                    flow_kind, return_target, created_at, expires_at)
                values (?, 'test-key', ?, ?, 'CUSTOMER_REGISTRATION', '/bff/csrf', ?, ?)
                """,
                new byte[] {1},
                new byte[] {2},
                new byte[] {3},
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(60)));

        assertEquals(1, inserted);
    }
}
