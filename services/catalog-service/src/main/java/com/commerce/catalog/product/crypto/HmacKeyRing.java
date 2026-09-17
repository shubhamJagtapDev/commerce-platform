package com.commerce.catalog.product.crypto;

import com.commerce.catalog.product.config.CatalogLifecycleProperties.HmacKeyRingProperties;
import com.commerce.catalog.product.config.HmacKeyProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class HmacKeyRing {
    public static final int MAX_RETAINED_KEYS = 4;

    private final HmacKey active;
    private final List<HmacKey> lookupOrder;

    public HmacKeyRing(HmacKeyRingProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.retained().size() > MAX_RETAINED_KEYS) {
            throw new IllegalArgumentException("HMAC key ring has too many retained keys");
        }

        active = new HmacKey(properties.active());
        List<HmacKey> allKeys = new ArrayList<>(properties.retained().size() + 1);
        allKeys.add(active);
        for (HmacKeyProperties retained : properties.retained()) {
            allKeys.add(new HmacKey(retained));
        }
        Set<String> ids = new HashSet<>();
        for (HmacKey key : allKeys) {
            if (!ids.add(key.id())) {
                throw new IllegalArgumentException("HMAC key IDs must be unique");
            }
        }
        allKeys.sort(Comparator.comparing(HmacKey::id));
        lookupOrder = List.copyOf(allKeys);
    }

    public HmacKey active() {
        return active;
    }

    public List<HmacKey> lookupOrder() {
        return lookupOrder;
    }

    @Override
    public String toString() {
        return "HmacKeyRing[keys=REDACTED]";
    }
}
