package com.commerce.catalog.product.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commerce.catalog.product.application.CreateDraftMutation;
import com.commerce.catalog.product.application.IdempotencyKey;
import com.commerce.catalog.product.application.PreparedLifecycleDigest;
import com.commerce.catalog.product.config.CatalogLifecycleProperties;
import com.commerce.catalog.product.config.CatalogLifecycleProperties.HmacKeyRingProperties;
import com.commerce.catalog.product.config.HmacKeyProperties;
import com.commerce.catalog.product.domain.OptionDimensions;
import com.commerce.catalog.product.domain.OptionValues;
import com.commerce.catalog.product.domain.category.CategoryId;
import com.commerce.catalog.product.domain.category.CategorySelection;
import com.commerce.catalog.product.domain.product_models.ImageUrls;
import com.commerce.catalog.product.domain.product_models.ProductContent;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.commerce.catalog.product.fingerprint.CatalogMutationCanonicalizer;
import com.commerce.catalog.product.fingerprint.LongDescriptionCanonicalizer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogLifecycleCryptoTest {
    private static final String ACTIVE_MATERIAL = material(0);
    private static final String RETAINED_MATERIAL = material(32);
    private static final String AUDIT_MATERIAL = material(64);

    @Test
    @SuppressWarnings("NullAway") // Exercises fail-fast behavior for externally bound missing configuration.
    void keyRingRejectsInvalidMaterialDuplicateIdsAndExcessRetention() {
        assertThatThrownBy(() -> new HmacKey(key(" ", ACTIVE_MATERIAL))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacKeyRing(new HmacKeyRingProperties(null, List.of())))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HmacKey(key("bad", "not-base64")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("not-base64");
        assertThatThrownBy(() -> new HmacKey(key("bad", Base64.getEncoder().encodeToString(new byte[31]))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacKeyRing(new HmacKeyRingProperties(
                        key("same", ACTIVE_MATERIAL), List.of(key("same", RETAINED_MATERIAL)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HmacKeyRing(new HmacKeyRingProperties(
                        key("active", ACTIVE_MATERIAL),
                        List.of(
                                key("one", RETAINED_MATERIAL),
                                key("two", RETAINED_MATERIAL),
                                key("three", RETAINED_MATERIAL),
                                key("four", RETAINED_MATERIAL),
                                key("five", RETAINED_MATERIAL)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleAndAuditConfigurationRequireSeparateKeysAndTheContractualRetention() {
        HmacKeyRingProperties sharedLifecycle = new HmacKeyRingProperties(key("lifecycle", ACTIVE_MATERIAL), List.of());
        HmacKeyRingProperties sharedAudit = new HmacKeyRingProperties(key("audit", ACTIVE_MATERIAL), List.of());

        assertThatThrownBy(() -> new CatalogLifecycleKeyRings(
                        new CatalogLifecycleProperties(Duration.ofHours(24), sharedLifecycle, sharedAudit)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(ACTIVE_MATERIAL);
        assertThatThrownBy(() -> new CatalogLifecycleProperties(Duration.ofHours(23), sharedLifecycle, sharedAudit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lookupUsesDeterministicKeyIdOrderWhileFreshUsesTheActiveKey() {
        CatalogLifecycleProperties properties = properties();
        CatalogLifecycleKeyRings rings = new CatalogLifecycleKeyRings(properties);
        LifecycleDigestService service = new LifecycleDigestService(
                new HmacSha256(), rings, new CatalogMutationCanonicalizer(new LongDescriptionCanonicalizer()));

        List<PreparedLifecycleDigest> lookup =
                service.prepareLookup(new IdempotencyKey("key_123456789012"), new CreateDraftMutation(candidate()));

        assertThat(lookup).extracting(PreparedLifecycleDigest::hashKeyId).containsExactly("a-old", "z-active");
        assertThat(service.prepareFresh(new IdempotencyKey("key_123456789012"), new CreateDraftMutation(candidate()))
                        .hashKeyId())
                .isEqualTo("z-active");
        PreparedLifecycleDigest fresh =
                service.prepareFresh(new IdempotencyKey("key_123456789012"), new CreateDraftMutation(candidate()));
        assertThat(hex(fresh.keyHash())).isEqualTo("cff71deb628c809f76eadd0175a1815fe0b3b4edf1d561fe833af5b61c6a9ab7");
        assertThat(hex(fresh.requestFingerprint()))
                .isEqualTo("bbd8bce2ee622623ea1ef1806e473223fd7ecd88095cfcdf05aef01001a8d03d");
    }

    @Test
    void hmacPurposesAndSeparateKeyRingsProduceStableDistinctDigests() {
        HmacSha256 hmac = new HmacSha256();
        HmacKey lifecycleKey = new HmacKey(key("lifecycle", ACTIVE_MATERIAL));
        byte[] message = "fixed-message".getBytes(StandardCharsets.UTF_8);

        byte[] keyDigest = hmac.digest(lifecycleKey, LifecycleDigestService.IDEMPOTENCY_KEY_PURPOSE, message);
        byte[] fingerprintDigest =
                hmac.digest(lifecycleKey, LifecycleDigestService.REQUEST_FINGERPRINT_PURPOSE, message);
        AuditActorDigest actor = new AuditActorPseudonymizer(hmac, new CatalogLifecycleKeyRings(properties()))
                .pseudonymize("https://issuer.example.test", "actor-123");

        assertThat(hex(keyDigest)).isEqualTo("18029b920f143f05e863fc52852e8dad824d1a000eff2ad4b3f25bb30f109a02");
        assertThat(hex(fingerprintDigest))
                .isEqualTo("5e717b586cc383fb4e45bead522869dd92a975dbf85008eed3e8331d562fd414");
        assertThat(actor.keyId()).isEqualTo("audit-active");
        assertThat(actor.actorHash()).isNotEqualTo(keyDigest).isNotEqualTo(fingerprintDigest);
        assertThat(actor.toString())
                .doesNotContain("https://issuer.example.test", "actor-123", AUDIT_MATERIAL)
                .contains("REDACTED");
    }

    @Test
    void configurationAndDigestOutputRedactSecretMaterial() {
        CatalogLifecycleProperties properties = properties();
        HmacKeyRing ring = new HmacKeyRing(properties.lifecycleKeys());

        assertThat(properties.toString()).doesNotContain(ACTIVE_MATERIAL, RETAINED_MATERIAL);
        assertThat(properties.lifecycleKeys().toString()).doesNotContain(ACTIVE_MATERIAL, RETAINED_MATERIAL);
        assertThat(properties.lifecycleKeys().active().toString()).doesNotContain(ACTIVE_MATERIAL);
        assertThat(ring.toString()).doesNotContain(ACTIVE_MATERIAL, RETAINED_MATERIAL);
    }

    private static CatalogLifecycleProperties properties() {
        return new CatalogLifecycleProperties(
                Duration.ofHours(24),
                new HmacKeyRingProperties(key("z-active", ACTIVE_MATERIAL), List.of(key("a-old", RETAINED_MATERIAL))),
                new HmacKeyRingProperties(key("audit-active", AUDIT_MATERIAL), List.of()));
    }

    private static HmacKeyProperties key(String id, String material) {
        return new HmacKeyProperties(id, material);
    }

    private static String material(int firstByte) {
        byte[] bytes = new byte[32];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (firstByte + index);
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static PublicationCandidate candidate() {
        return new PublicationCandidate(
                new ProductContent("Product", "Description", ImageUrls.none(), null),
                new CategorySelection(new CategoryId(UUID.fromString("10000000-0000-0000-0000-000000000001")), null),
                OptionDimensions.none(),
                List.of(new ConfiguredVariant(
                        new VariantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                        OptionValues.none(),
                        false,
                        null)));
    }
}
