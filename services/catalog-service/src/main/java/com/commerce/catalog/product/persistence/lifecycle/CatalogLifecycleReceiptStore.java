package com.commerce.catalog.product.persistence.lifecycle;

import com.commerce.catalog.product.application.CommandExecutionResult;
import com.commerce.catalog.product.application.CommandOutcome;
import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.config.CatalogLifecycleProperties;
import com.commerce.catalog.product.domain.product_models.ProductCommandResultState;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_models.ProductLifecycleOperation;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.commerce.catalog.product.receipt.LifecycleReceipt;
import com.commerce.catalog.product.receipt.LifecycleReceiptIntegrityException;
import com.commerce.catalog.product.receipt.LifecycleReceiptReplayPolicy;
import com.commerce.catalog.product.receipt.ReceiptReplayDecision;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public final class CatalogLifecycleReceiptStore {
    private final CatalogLifecycleCommandRepository repository;
    private final EntityManager entityManager;
    private final LifecycleReceiptReplayPolicy replayPolicy;
    private final Duration receiptRetention;

    public CatalogLifecycleReceiptStore(
            CatalogLifecycleCommandRepository repository,
            EntityManager entityManager,
            LifecycleReceiptReplayPolicy replayPolicy,
            CatalogLifecycleProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.replayPolicy = Objects.requireNonNull(replayPolicy, "replayPolicy");
        this.receiptRetention = Objects.requireNonNull(properties, "properties").receiptRetention();
    }

    public ReceiptReplayDecision resolve(
            UUID grantId,
            ProductLifecycleOperation operation,
            List<PreparedLifecycleDigest> candidateDigests,
            Instant transactionTime) {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(candidateDigests, "candidateDigests");
        Objects.requireNonNull(transactionTime, "transactionTime");

        List<PreparedLifecycleDigest> orderedDigests = orderedUniqueDigests(candidateDigests);
        List<LockedMatch> matches = new ArrayList<>();
        for (PreparedLifecycleDigest digest : orderedDigests) {
            repository
                    .lockByReplayScope(grantId, operation.name(), digest.keyHash())
                    .ifPresent(entity -> matches.add(new LockedMatch(entity, digest)));
        }
        if (matches.size() > 1) {
            throw new LifecycleReceiptIntegrityException("Multiple lifecycle receipts matched one replay lookup");
        }
        if (matches.isEmpty()) {
            return replayPolicy.resolve(null, null, transactionTime);
        }

        LockedMatch match = matches.getFirst();
        LifecycleReceipt receipt = toReceipt(match.entity());
        if (!receipt.hashKeyId().equals(match.digest().hashKeyId())) {
            throw new LifecycleReceiptIntegrityException("Lifecycle receipt key ID does not match its replay scope");
        }
        ReceiptReplayDecision decision = replayPolicy.resolve(receipt, match.digest(), transactionTime);
        if (decision == ReceiptReplayDecision.Expired.INSTANCE) {
            repository.delete(match.entity());
            repository.flush();
            return ReceiptReplayDecision.Fresh.INSTANCE;
        }
        return decision;
    }

    public void append(UUID grantId, PreparedLifecycleDigest digest, CommandExecutionResult executionResult) {
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(executionResult, "executionResult");
        if (executionResult.replayed()) {
            throw new IllegalArgumentException("A replay must not append another receipt");
        }
        CommandOutcome outcome = executionResult.outcome();
        entityManager.persist(new CatalogLifecycleCommandEntity(
                outcome.commandId(),
                grantId,
                outcome.operation().name(),
                digest.hashKeyId(),
                digest.keyHash(),
                digest.requestFingerprint(),
                outcome.productId().value(),
                outcome.variantId() == null ? null : outcome.variantId().value(),
                outcome.resultVersion(),
                outcome.resultState().name(),
                outcome.publicationId(),
                (short) executionResult.originalHttpStatus(),
                outcome.appliedAt(),
                outcome.appliedAt().plus(receiptRetention)));
    }

    private static List<PreparedLifecycleDigest> orderedUniqueDigests(List<PreparedLifecycleDigest> candidateDigests) {
        if (candidateDigests.isEmpty()) {
            throw new IllegalArgumentException("candidateDigests must not be empty");
        }
        Set<String> keyIds = new HashSet<>();
        for (PreparedLifecycleDigest digest : candidateDigests) {
            if (!keyIds.add(digest.hashKeyId())) {
                throw new IllegalArgumentException("candidate digest key IDs must be unique");
            }
        }
        return candidateDigests.stream()
                .sorted(Comparator.comparing(PreparedLifecycleDigest::hashKeyId))
                .toList();
    }

    private static LifecycleReceipt toReceipt(CatalogLifecycleCommandEntity entity) {
        CommandOutcome outcome = new CommandOutcome(
                entity.id(),
                ProductLifecycleOperation.valueOf(entity.operationCode()),
                new ProductId(entity.productId()),
                entity.variantId() == null ? null : new VariantId(entity.variantId()),
                entity.resultVersion(),
                ProductCommandResultState.valueOf(entity.resultState()),
                entity.publicationId(),
                entity.appliedAt());
        return new LifecycleReceipt(
                entity.hashKeyId(),
                entity.keyHash(),
                entity.requestFingerprint(),
                outcome,
                entity.httpStatus(),
                entity.expiresAt());
    }

    private record LockedMatch(CatalogLifecycleCommandEntity entity, PreparedLifecycleDigest digest) {
        private LockedMatch {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(digest, "digest");
        }
    }
}
