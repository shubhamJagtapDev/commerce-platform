package com.commerce.catalog.product.receipt;

import static org.assertj.core.api.Assertions.assertThat;

import com.commerce.catalog.product.application.CommandOutcome;
import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleReceiptReplayPolicyTest {
    private static final Instant APPLIED_AT = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-17T00:00:00Z");

    private final LifecycleReceiptReplayPolicy policy = new LifecycleReceiptReplayPolicy();

    @Test
    void absentReceiptIsFresh() {
        assertThat(policy.resolve(null, null, APPLIED_AT)).isEqualTo(ReceiptReplayDecision.Fresh.INSTANCE);
    }

    @Test
    void liveMatchingFingerprintReplaysTheExactHistoricalResult() {
        LifecycleReceipt receipt = receipt(digest((byte) 1));

        ReceiptReplayDecision decision = policy.resolve(receipt, digest((byte) 1), EXPIRES_AT.minusNanos(1));

        assertThat(decision).isInstanceOfSatisfying(ReceiptReplayDecision.Replay.class, replay -> {
            assertThat(replay.result().outcome()).isEqualTo(receipt.outcome());
            assertThat(replay.result().originalHttpStatus()).isEqualTo(201);
            assertThat(replay.result().replayed()).isTrue();
            assertThat(replay.result().location())
                    .isEqualTo("/api/v1/catalog/maintenance/products/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        });
        assertThat(receipt.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void liveDifferentFingerprintConflictsAndExpiryBoundaryIsFreshEligible() {
        LifecycleReceipt receipt = receipt(digest((byte) 1));

        assertThat(policy.resolve(receipt, digest((byte) 2), EXPIRES_AT.minusSeconds(1)))
                .isEqualTo(ReceiptReplayDecision.Conflict.INSTANCE);
        assertThat(policy.resolve(receipt, digest((byte) 1), EXPIRES_AT))
                .isEqualTo(ReceiptReplayDecision.Expired.INSTANCE);
    }

    private static LifecycleReceipt receipt(PreparedLifecycleDigest digest) {
        CommandOutcome outcome = new CommandOutcome(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                ProductLifecycleOperation.CREATE_DRAFT,
                new ProductId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                null,
                0,
                ProductCommandResultState.DRAFT,
                null,
                APPLIED_AT);
        return new LifecycleReceipt(
                digest.hashKeyId(), digest.keyHash(), digest.requestFingerprint(), outcome, 201, EXPIRES_AT);
    }

    private static PreparedLifecycleDigest digest(byte fingerprintByte) {
        byte[] keyHash = new byte[32];
        keyHash[0] = 1;
        byte[] fingerprint = new byte[32];
        fingerprint[0] = fingerprintByte;
        return new PreparedLifecycleDigest("key-1", keyHash, fingerprint);
    }
}
