package com.commerce.catalog.product.fingerprint;

import com.commerce.catalog.product.application.CatalogMutation;
import com.commerce.catalog.product.application.CreateDraftMutation;
import com.commerce.catalog.product.application.LifecycleMutation;
import com.commerce.catalog.product.application.ReplaceCandidateMutation;
import com.commerce.catalog.product.domain.InrMoney;
import com.commerce.catalog.product.domain.product_models.ProductId;
import com.commerce.catalog.product.domain.product_publication.PublicationCandidate;
import com.commerce.catalog.product.domain.product_variants.ConfiguredVariant;
import com.commerce.catalog.product.domain.product_variants.VariantId;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class CatalogMutationCanonicalizer {
    public static final String FORMAT = "catalog-product-mutation/v1";

    private static final Comparator<ConfiguredVariant> VARIANT_ID_ORDER =
            Comparator.comparing(variant -> variant.variantId().value().toString());

    private final JsonFactory jsonFactory = new JsonFactory();
    private final LongDescriptionCanonicalizer longDescriptionCanonicalizer;

    public CatalogMutationCanonicalizer(LongDescriptionCanonicalizer longDescriptionCanonicalizer) {
        this.longDescriptionCanonicalizer =
                Objects.requireNonNull(longDescriptionCanonicalizer, "longDescriptionCanonicalizer");
    }

    public byte[] canonicalize(CatalogMutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("format", FORMAT);
            generator.writeStringField("operation", mutation.operation().name());
            if (mutation instanceof CreateDraftMutation createDraft) {
                writeNullableProductId(generator, null);
                writeNullableVariantId(generator, null);
                generator.writeNullField("expectedVersion");
                writeCandidate(generator, createDraft.candidate());
            } else if (mutation instanceof ReplaceCandidateMutation replaceCandidate) {
                writeNullableProductId(generator, replaceCandidate.productId());
                writeNullableVariantId(generator, null);
                generator.writeNumberField("expectedVersion", replaceCandidate.expectedVersion());
                writeCandidate(generator, replaceCandidate.candidate());
            } else if (mutation instanceof LifecycleMutation lifecycle) {
                writeNullableProductId(generator, lifecycle.productId());
                writeNullableVariantId(generator, lifecycle.variantId());
                generator.writeNumberField("expectedVersion", lifecycle.expectedVersion());
                generator.writeNullField("candidate");
            }
            generator.writeEndObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to canonicalize Catalog mutation", exception);
        }
        return output.toByteArray();
    }

    private void writeCandidate(JsonGenerator generator, PublicationCandidate candidate) throws IOException {
        generator.writeObjectFieldStart("candidate");
        writeNullableString(generator, "name", candidate.content().name());
        writeNullableString(generator, "shortDescription", candidate.content().shortDescription());
        generator.writeStringField(
                "mainCategoryId", candidate.category().mainCategoryId().value().toString());
        if (candidate.category().subcategoryId() == null) {
            generator.writeNullField("subcategoryId");
        } else {
            generator.writeStringField(
                    "subcategoryId",
                    candidate.category().subcategoryId().value().toString());
        }
        generator.writeArrayFieldStart("imageUrls");
        for (String imageUrl : candidate.content().imageUrls().values()) {
            generator.writeString(imageUrl);
        }
        generator.writeEndArray();
        generator.writeFieldName("longDescription");
        if (candidate.content().longDescription() == null) {
            generator.writeNull();
        } else {
            byte[] canonicalLongDescription = longDescriptionCanonicalizer.canonicalize(
                    candidate.content().longDescription());
            generator.writeRawValue(new String(canonicalLongDescription, StandardCharsets.UTF_8));
        }
        generator.writeArrayFieldStart("optionDimensions");
        for (String dimension : candidate.optionDimensions().names()) {
            generator.writeString(dimension);
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("variants");
        List<ConfiguredVariant> variants =
                candidate.variants().stream().sorted(VARIANT_ID_ORDER).toList();
        for (ConfiguredVariant variant : variants) {
            writeVariant(generator, variant);
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeVariant(JsonGenerator generator, ConfiguredVariant variant) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("variantId", variant.variantId().value().toString());
        generator.writeArrayFieldStart("optionValues");
        for (String optionValue : variant.optionValues().values()) {
            generator.writeString(optionValue);
        }
        generator.writeEndArray();
        generator.writeBooleanField("sellable", variant.sellable());
        generator.writeFieldName("price");
        InrMoney price = variant.price();
        if (price == null) {
            generator.writeNull();
        } else {
            generator.writeStartObject();
            generator.writeStringField("amount", price.responseAmount());
            generator.writeStringField("currency", InrMoney.CURRENCY);
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    private static void writeNullableProductId(JsonGenerator generator, @Nullable ProductId productId)
            throws IOException {
        if (productId == null) {
            generator.writeNullField("productId");
        } else {
            generator.writeStringField("productId", productId.value().toString());
        }
    }

    private static void writeNullableVariantId(JsonGenerator generator, @Nullable VariantId variantId)
            throws IOException {
        if (variantId == null) {
            generator.writeNullField("variantId");
        } else {
            generator.writeStringField("variantId", variantId.value().toString());
        }
    }

    private static void writeNullableString(JsonGenerator generator, String fieldName, @Nullable String value)
            throws IOException {
        if (value == null) {
            generator.writeNullField(fieldName);
        } else {
            generator.writeStringField(fieldName, value);
        }
    }
}
