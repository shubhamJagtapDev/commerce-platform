package com.commerce.catalog.catalogsecurity;

import com.commerce.catalog.catalogsecurity.config.CatalogSecurityProperties;
import com.commerce.catalog.catalogsecurity.exceptions.CatalogAuthorizationException;
import com.commerce.catalog.catalogsecurity.exceptions.IdempotencyConflictException;
import com.commerce.catalog.catalogsecurity.models.CatalogAuthorizationProbeEntity;
import com.commerce.catalog.catalogsecurity.models.CatalogCommandIdempotencyEntity;
import com.commerce.catalog.catalogsecurity.models.CatalogMaintainerGrantEntity;
import com.commerce.catalog.catalogsecurity.repositories.CatalogAuthorizationProbeRepository;
import com.commerce.catalog.catalogsecurity.repositories.CatalogCommandIdempotencyRepository;
import com.commerce.catalog.catalogsecurity.repositories.CatalogMaintainerGrantRepository;
import com.commerce.catalog.catalogsecurity.utils.CatalogIdempotencyHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateAuthorizationProbe {
    static final String PURPOSE = "COM_46_AUTHORIZATION_GATE";
    private static final String OPERATION_CODE = "CREATE_AUTHORIZATION_PROBE";

    private final CatalogMaintainerGrantRepository grantRepository;
    private final CatalogAuthorizationProbeRepository probeRepository;
    private final CatalogCommandIdempotencyRepository idempotencyRepository;
    private final CatalogIdempotencyHasher hasher;
    private final CatalogSecurityProperties properties;
    private final Clock clock;

    CreateAuthorizationProbe(
            CatalogMaintainerGrantRepository grantRepository,
            CatalogAuthorizationProbeRepository probeRepository,
            CatalogCommandIdempotencyRepository idempotencyRepository,
            CatalogIdempotencyHasher hasher,
            CatalogSecurityProperties properties,
            Clock clock) {
        this.grantRepository = grantRepository;
        this.probeRepository = probeRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Result create(String issuer, String subject, String idempotencyKey, String purpose) {
        if (!PURPOSE.equals(purpose)) {
            throw new IllegalArgumentException("Unsupported authorization probe purpose");
        }
        if (idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Invalid idempotency key length");
        }

        CatalogMaintainerGrantEntity grant =
                grantRepository.lockActive(issuer, subject).orElseThrow(CatalogAuthorizationException::new);
        byte[] keyHash = hasher.hash("catalog-idempotency-key", idempotencyKey);
        byte[] requestFingerprint = hasher.hash("catalog-authorization-probe", purpose);
        CatalogCommandIdempotencyEntity existing = idempotencyRepository
                .lockByScope(issuer, subject, OPERATION_CODE, keyHash)
                .orElse(null);
        if (existing != null) {
            return replay(existing, requestFingerprint);
        }

        Instant now = clock.instant();
        CatalogCommandIdempotencyEntity claim = idempotencyRepository.save(new CatalogCommandIdempotencyEntity(
                UUID.randomUUID(),
                issuer,
                subject,
                OPERATION_CODE,
                keyHash,
                requestFingerprint,
                now,
                now.plus(properties.idempotencyRetention())));
        CatalogAuthorizationProbeEntity probe =
                probeRepository.save(new CatalogAuthorizationProbeEntity(UUID.randomUUID(), grant.getId(), now));
        claim.complete(probe.getId(), now);
        return Result.created(probe);
    }

    private Result replay(CatalogCommandIdempotencyEntity claim, byte[] requestFingerprint) {
        if (!Arrays.equals(claim.getRequestFingerprint(), requestFingerprint) || !claim.completed()) {
            throw new IdempotencyConflictException();
        }
        UUID probeId = claim.getResultProbeId();
        if (probeId == null) {
            throw new IdempotencyConflictException();
        }
        return Result.replayed(probeRepository.findById(probeId).orElseThrow(IllegalStateException::new));
    }

    public record Result(UUID probeId, long version, Instant committedAt, boolean created) {
        static Result created(CatalogAuthorizationProbeEntity probe) {
            return new Result(probe.getId(), probe.getVersion(), probe.getCommittedAt(), true);
        }

        static Result replayed(CatalogAuthorizationProbeEntity probe) {
            return new Result(probe.getId(), probe.getVersion(), probe.getCommittedAt(), false);
        }
    }
}
