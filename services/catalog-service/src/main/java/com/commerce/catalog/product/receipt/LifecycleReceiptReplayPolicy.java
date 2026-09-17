package com.commerce.catalog.product.receipt;

import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class LifecycleReceiptReplayPolicy {
    public ReceiptReplayDecision resolve(
            @Nullable LifecycleReceipt receipt,
            @Nullable PreparedLifecycleDigest incomingDigest,
            Instant transactionTime) {
        Objects.requireNonNull(transactionTime, "transactionTime");
        if (receipt == null) {
            if (incomingDigest != null) {
                throw new IllegalArgumentException("incomingDigest requires a receipt");
            }
            return ReceiptReplayDecision.Fresh.INSTANCE;
        }
        Objects.requireNonNull(incomingDigest, "incomingDigest");
        if (!receipt.hashKeyId().equals(incomingDigest.hashKeyId())
                || !MessageDigest.isEqual(receipt.keyHash(), incomingDigest.keyHash())) {
            throw new IllegalArgumentException("incoming digest does not match the locked receipt scope");
        }
        if (!receipt.expiresAt().isAfter(transactionTime)) {
            return ReceiptReplayDecision.Expired.INSTANCE;
        }
        if (MessageDigest.isEqual(receipt.requestFingerprint(), incomingDigest.requestFingerprint())) {
            return new ReceiptReplayDecision.Replay(receipt.replayResult());
        }
        return ReceiptReplayDecision.Conflict.INSTANCE;
    }
}
