package com.commerce.catalog.product.crypto;

import com.commerce.catalog.product.config.CatalogLifecycleProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class CatalogLifecycleKeyRings {
    private final HmacKeyRing lifecycle;
    private final HmacKeyRing audit;

    public CatalogLifecycleKeyRings(CatalogLifecycleProperties properties) {
        Objects.requireNonNull(properties, "properties");
        lifecycle = new HmacKeyRing(properties.lifecycleKeys());
        audit = new HmacKeyRing(properties.auditKeys());
        for (HmacKey lifecycleKey : lifecycle.lookupOrder()) {
            for (HmacKey auditKey : audit.lookupOrder()) {
                if (lifecycleKey.hasSameMaterial(auditKey)) {
                    throw new IllegalArgumentException("Lifecycle and audit HMAC key material must be separate");
                }
            }
        }
    }

    public HmacKeyRing lifecycle() {
        return lifecycle;
    }

    public HmacKeyRing audit() {
        return audit;
    }

    @Override
    public String toString() {
        return "CatalogLifecycleKeyRings[REDACTED]";
    }
}
