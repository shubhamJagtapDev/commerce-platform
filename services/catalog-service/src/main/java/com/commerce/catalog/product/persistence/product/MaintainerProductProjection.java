package com.commerce.catalog.product.persistence.product;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface MaintainerProductProjection {
    UUID getProductId();

    long getVersion();

    String getState();

    @Nullable
    String getCandidateName();

    @Nullable
    UUID getCurrentPublicationId();
}
