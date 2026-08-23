package com.commerce.catalog.catalogsecurity;

import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
class LocalCatalogMaintainerGrantFixture implements ApplicationRunner {
    private final CatalogMaintainerGrantRepository grantRepository;
    private final CatalogSecurityProperties properties;
    private final Clock clock;

    LocalCatalogMaintainerGrantFixture(
            CatalogMaintainerGrantRepository grantRepository, CatalogSecurityProperties properties, Clock clock) {
        this.grantRepository = grantRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String subject = properties.fixtureSubject();
        if (subject == null || subject.isBlank()) {
            return;
        }
        if (grantRepository.findByIssuerAndSubject(properties.issuer(), subject).isEmpty()) {
            grantRepository.save(
                    new CatalogMaintainerGrantEntity(UUID.randomUUID(), properties.issuer(), subject, clock.instant()));
        }
    }
}
