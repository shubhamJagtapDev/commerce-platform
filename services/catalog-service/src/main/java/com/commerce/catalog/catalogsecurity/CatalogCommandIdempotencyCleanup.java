package com.commerce.catalog.catalogsecurity;

import com.commerce.catalog.catalogsecurity.repositories.CatalogCommandIdempotencyRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CatalogCommandIdempotencyCleanup {
    private final CatalogCommandIdempotencyRepository idempotencyRepository;
    private final Clock clock;

    CatalogCommandIdempotencyCleanup(CatalogCommandIdempotencyRepository idempotencyRepository, Clock clock) {
        this.idempotencyRepository = idempotencyRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.catalog-security.idempotency-cleanup-interval:PT1H}")
    @Transactional
    void deleteExpiredClaims() {
        idempotencyRepository.deleteExpiredAtOrBefore(clock.instant());
    }
}
