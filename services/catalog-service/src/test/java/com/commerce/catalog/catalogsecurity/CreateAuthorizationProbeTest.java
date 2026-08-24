package com.commerce.catalog.catalogsecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.commerce.catalog.catalogsecurity.config.CatalogSecurityProperties;
import com.commerce.catalog.catalogsecurity.exceptions.CatalogAuthorizationException;
import com.commerce.catalog.catalogsecurity.models.CatalogAuthorizationProbeEntity;
import com.commerce.catalog.catalogsecurity.models.CatalogCommandIdempotencyEntity;
import com.commerce.catalog.catalogsecurity.models.CatalogMaintainerGrantEntity;
import com.commerce.catalog.catalogsecurity.repositories.CatalogAuthorizationProbeRepository;
import com.commerce.catalog.catalogsecurity.repositories.CatalogCommandIdempotencyRepository;
import com.commerce.catalog.catalogsecurity.repositories.CatalogMaintainerGrantRepository;
import com.commerce.catalog.catalogsecurity.utils.CatalogIdempotencyHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateAuthorizationProbeTest {
    private static final Instant NOW = Instant.parse("2026-08-23T10:15:30Z");
    private static final String ISSUER = "http://issuer.example/realms/commerce";
    private static final String SUBJECT = "maintainer-subject";
    private static final String IDEMPOTENCY_KEY = "0123456789abcdef";

    private final CatalogMaintainerGrantRepository grantRepository = mock(CatalogMaintainerGrantRepository.class);
    private final CatalogAuthorizationProbeRepository probeRepository = mock(CatalogAuthorizationProbeRepository.class);
    private final CatalogCommandIdempotencyRepository idempotencyRepository =
            mock(CatalogCommandIdempotencyRepository.class);
    private CreateAuthorizationProbe useCase;

    @BeforeEach
    void setUp() {
        CatalogSecurityProperties properties = new CatalogSecurityProperties(
                ISSUER,
                "http://keycloak.example/certs",
                "catalog-api",
                "identity-access-bff",
                Base64.getEncoder().encodeToString(new byte[32]),
                Duration.ofHours(24),
                null);
        useCase = new CreateAuthorizationProbe(
                grantRepository,
                probeRepository,
                idempotencyRepository,
                new CatalogIdempotencyHasher(properties),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeGrantCommitsOneAuthorizationProbe() {
        CatalogMaintainerGrantEntity grant = new CatalogMaintainerGrantEntity(UUID.randomUUID(), ISSUER, SUBJECT, NOW);
        AtomicReference<CatalogCommandIdempotencyEntity> managedClaim = new AtomicReference<>();
        when(grantRepository.lockActive(ISSUER, SUBJECT)).thenReturn(Optional.of(grant));
        when(idempotencyRepository.lockByScope(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(idempotencyRepository.save(any())).thenAnswer(invocation -> {
            CatalogCommandIdempotencyEntity managed = new CatalogCommandIdempotencyEntity(
                    UUID.randomUUID(),
                    ISSUER,
                    SUBJECT,
                    "CREATE_AUTHORIZATION_PROBE",
                    new byte[32],
                    new byte[32],
                    NOW,
                    NOW.plusSeconds(60));
            managedClaim.set(managed);
            return managed;
        });
        when(probeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAuthorizationProbe.Result result =
                useCase.create(ISSUER, SUBJECT, IDEMPOTENCY_KEY, CreateAuthorizationProbe.PURPOSE);

        assertThat(result.created()).isTrue();
        assertThat(result.version()).isZero();
        assertThat(result.committedAt()).isEqualTo(NOW);
        assertThat(managedClaim.get())
                .satisfies(claim -> assertThat(claim.completed()).isTrue());
        verify(probeRepository).save(any(CatalogAuthorizationProbeEntity.class));
        verify(idempotencyRepository).save(any(CatalogCommandIdempotencyEntity.class));
    }

    @Test
    void missingGrantDeniesWithoutMutationOrIdempotencyState() {
        when(grantRepository.lockActive(ISSUER, SUBJECT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(ISSUER, SUBJECT, IDEMPOTENCY_KEY, CreateAuthorizationProbe.PURPOSE))
                .isInstanceOf(CatalogAuthorizationException.class);

        verify(probeRepository, never()).save(any());
        verify(idempotencyRepository, never()).save(any());
    }

    @Test
    void expiredClaimStartsANewAuthorizationProbe() {
        CatalogMaintainerGrantEntity grant = new CatalogMaintainerGrantEntity(UUID.randomUUID(), ISSUER, SUBJECT, NOW);
        CatalogCommandIdempotencyEntity expiredClaim = new CatalogCommandIdempotencyEntity(
                UUID.randomUUID(),
                ISSUER,
                SUBJECT,
                "CREATE_AUTHORIZATION_PROBE",
                new byte[32],
                new byte[32],
                NOW.minus(Duration.ofHours(25)),
                NOW.minus(Duration.ofHours(1)));
        when(grantRepository.lockActive(ISSUER, SUBJECT)).thenReturn(Optional.of(grant));
        when(idempotencyRepository.lockByScope(any(), any(), any(), any())).thenReturn(Optional.of(expiredClaim));
        when(probeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAuthorizationProbe.Result result =
                useCase.create(ISSUER, SUBJECT, IDEMPOTENCY_KEY, CreateAuthorizationProbe.PURPOSE);

        assertThat(result.created()).isTrue();
        assertThat(expiredClaim.completed()).isTrue();
        assertThat(expiredClaim.expiredAt(NOW)).isFalse();
        verify(idempotencyRepository, never()).save(any());
        verify(probeRepository).save(any(CatalogAuthorizationProbeEntity.class));
    }

    @Test
    void invalidProbePurposeIsRejectedBeforeGrantLookup() {
        assertThatThrownBy(() -> useCase.create(ISSUER, SUBJECT, IDEMPOTENCY_KEY, "PRODUCT_CREATE"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(grantRepository, never()).lockActive(any(), any());
    }
}
