package com.commerce.catalog.catalogsecurity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.commerce.catalog.catalogsecurity.repositories.CatalogCommandIdempotencyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CatalogCommandIdempotencyCleanupTest {
    @Test
    void cleanupDeletesClaimsAtTheCurrentTimeOrEarlier() {
        Instant now = Instant.parse("2026-08-24T10:15:30Z");
        CatalogCommandIdempotencyRepository repository = mock(CatalogCommandIdempotencyRepository.class);
        CatalogCommandIdempotencyCleanup cleanup =
                new CatalogCommandIdempotencyCleanup(repository, Clock.fixed(now, ZoneOffset.UTC));

        cleanup.deleteExpiredClaims();

        verify(repository).deleteExpiredAtOrBefore(now);
    }
}
