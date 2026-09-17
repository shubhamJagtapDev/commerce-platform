package com.commerce.catalog.product.receipt;

import com.commerce.catalog.product.application.CommandExecutionResult;
import java.util.Objects;

public sealed interface ReceiptReplayDecision
        permits ReceiptReplayDecision.Conflict,
                ReceiptReplayDecision.Expired,
                ReceiptReplayDecision.Fresh,
                ReceiptReplayDecision.Replay {
    enum Fresh implements ReceiptReplayDecision {
        INSTANCE
    }

    record Replay(CommandExecutionResult result) implements ReceiptReplayDecision {
        public Replay {
            Objects.requireNonNull(result, "result");
            if (!result.replayed()) {
                throw new IllegalArgumentException("replay result must be marked replayed");
            }
        }
    }

    enum Conflict implements ReceiptReplayDecision {
        INSTANCE
    }

    enum Expired implements ReceiptReplayDecision {
        INSTANCE
    }
}
