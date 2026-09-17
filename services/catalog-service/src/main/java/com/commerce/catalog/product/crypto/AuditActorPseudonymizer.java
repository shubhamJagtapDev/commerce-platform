package com.commerce.catalog.product.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class AuditActorPseudonymizer {
    public static final String PURPOSE = "catalog-lifecycle-audit-actor:v1";

    private final HmacSha256 hmac;
    private final HmacKeyRing auditKeys;

    public AuditActorPseudonymizer(HmacSha256 hmac, CatalogLifecycleKeyRings keyRings) {
        this.hmac = Objects.requireNonNull(hmac, "hmac");
        this.auditKeys = Objects.requireNonNull(keyRings, "keyRings").audit();
    }

    public AuditActorDigest pseudonymize(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("issuer and subject must not be blank");
        }
        byte[] issuerBytes = issuer.getBytes(StandardCharsets.UTF_8);
        byte[] subjectBytes = subject.getBytes(StandardCharsets.UTF_8);
        ByteBuffer input =
                ByteBuffer.allocate(Integer.BYTES + issuerBytes.length + Integer.BYTES + subjectBytes.length);
        input.putInt(issuerBytes.length)
                .put(issuerBytes)
                .putInt(subjectBytes.length)
                .put(subjectBytes);

        HmacKey key = auditKeys.active();
        return new AuditActorDigest(key.id(), hmac.digest(key, PURPOSE, input.array()));
    }
}
