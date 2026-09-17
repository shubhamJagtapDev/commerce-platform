package com.commerce.catalog.product.crypto;

import com.commerce.catalog.product.application.CatalogMutation;
import com.commerce.catalog.product.application.IdempotencyKey;
import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.fingerprint.CatalogMutationCanonicalizer;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("StringConcatToTextBlock") // Error Prone 2.50 crashes on this Java 25 compilation unit.
public final class LifecycleDigestService {
    public static final String IDEMPOTENCY_KEY_PURPOSE = "catalog-lifecycle-idempotency-key:v1";
    public static final String REQUEST_FINGERPRINT_PURPOSE = "catalog-lifecycle-request-fingerprint:v1";

    private final HmacSha256 hmac;
    private final HmacKeyRing lifecycleKeys;
    private final CatalogMutationCanonicalizer canonicalizer;

    public LifecycleDigestService(
            HmacSha256 hmac, CatalogLifecycleKeyRings keyRings, CatalogMutationCanonicalizer canonicalizer) {
        this.hmac = Objects.requireNonNull(hmac, "hmac");
        this.lifecycleKeys = Objects.requireNonNull(keyRings, "keyRings").lifecycle();
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public PreparedLifecycleDigest prepareFresh(IdempotencyKey idempotencyKey, CatalogMutation mutation) {
        return prepare(lifecycleKeys.active(), idempotencyKey, canonicalizer.canonicalize(mutation));
    }

    public List<PreparedLifecycleDigest> prepareLookup(IdempotencyKey idempotencyKey, CatalogMutation mutation) {
        byte[] canonicalMutation = canonicalizer.canonicalize(mutation);
        return lifecycleKeys.lookupOrder().stream()
                .map(key -> prepare(key, idempotencyKey, canonicalMutation))
                .toList();
    }

    private PreparedLifecycleDigest prepare(HmacKey key, IdempotencyKey idempotencyKey, byte[] canonicalMutation) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return new PreparedLifecycleDigest(
                key.id(),
                hmac.digest(key, IDEMPOTENCY_KEY_PURPOSE, idempotencyKey.utf8Bytes()),
                hmac.digest(key, REQUEST_FINGERPRINT_PURPOSE, canonicalMutation));
    }
}
