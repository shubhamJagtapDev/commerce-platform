package com.commerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.config.CatalogLifecycleProperties;
import com.commerce.catalog.product.config.CatalogLifecycleProperties.HmacKeyRingProperties;
import com.commerce.catalog.product.config.HmacKeyProperties;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.persistence.lifecycle.CatalogLifecycleCommandRepository;
import com.commerce.catalog.product.persistence.lifecycle.CatalogLifecycleReceiptStore;
import com.commerce.catalog.product.receipt.LifecycleReceiptReplayPolicy;
import com.commerce.catalog.product.receipt.ReceiptReplayDecision;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CatalogLifecycleReceiptStoreOrderTest {
    @Test
    void candidateScopesAreLockedInKeyIdOrder() {
        CatalogLifecycleCommandRepository repository = mock(CatalogLifecycleCommandRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        UUID grantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        PreparedLifecycleDigest last = digest("z-key", (byte) 2);
        PreparedLifecycleDigest first = digest("a-key", (byte) 1);
        when(repository.lockByReplayScope(eq(grantId), eq("PUBLISH"), aryEq(first.keyHash())))
                .thenReturn(Optional.empty());
        when(repository.lockByReplayScope(eq(grantId), eq("PUBLISH"), aryEq(last.keyHash())))
                .thenReturn(Optional.empty());
        CatalogLifecycleReceiptStore store = new CatalogLifecycleReceiptStore(
                repository, entityManager, new LifecycleReceiptReplayPolicy(), properties());

        ReceiptReplayDecision decision = store.resolve(
                grantId,
                ProductLifecycleOperation.PUBLISH,
                List.of(last, first),
                Instant.parse("2026-09-16T00:00:00Z"));

        assertThat(decision).isEqualTo(ReceiptReplayDecision.Fresh.INSTANCE);
        InOrder order = inOrder(repository);
        order.verify(repository).lockByReplayScope(eq(grantId), eq("PUBLISH"), aryEq(first.keyHash()));
        order.verify(repository).lockByReplayScope(eq(grantId), eq("PUBLISH"), aryEq(last.keyHash()));
    }

    private static PreparedLifecycleDigest digest(String keyId, byte keyByte) {
        byte[] keyHash = new byte[32];
        keyHash[0] = keyByte;
        return new PreparedLifecycleDigest(keyId, keyHash, new byte[32]);
    }

    private static CatalogLifecycleProperties properties() {
        String material = Base64.getEncoder().encodeToString(new byte[32]);
        HmacKeyProperties key = new HmacKeyProperties("unused", material);
        return new CatalogLifecycleProperties(
                Duration.ofHours(24),
                new HmacKeyRingProperties(key, List.of()),
                new HmacKeyRingProperties(key, List.of()));
    }
}
