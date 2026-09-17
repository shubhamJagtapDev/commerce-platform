CREATE FUNCTION catalog_assert_publication_can_change(target_publication_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    parent_sealed BOOLEAN;
BEGIN
    SELECT sealed
    INTO parent_sealed
    FROM catalog_publication
    WHERE id = target_publication_id
    FOR UPDATE;

    IF parent_sealed IS NULL THEN
        RAISE EXCEPTION 'catalog publication % does not exist', target_publication_id
            USING ERRCODE = '23514';
    END IF;

    IF parent_sealed THEN
        RAISE EXCEPTION 'sealed catalog publication % is immutable', target_publication_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION catalog_guard_publication_header()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    configured_variant_count INTEGER;
    configured_sellable_count INTEGER;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.sealed THEN
            RAISE EXCEPTION 'sealed catalog publication % cannot be deleted', OLD.id
                USING ERRCODE = '23514';
        END IF;

        RETURN OLD;
    END IF;

    IF OLD.sealed THEN
        RAISE EXCEPTION 'sealed catalog publication % is immutable', OLD.id
            USING ERRCODE = '23514';
    END IF;

    IF NOT (
        OLD.id = NEW.id
        AND OLD.product_id = NEW.product_id
        AND OLD.product_version = NEW.product_version
        AND OLD.name = NEW.name
        AND OLD.short_description = NEW.short_description
        AND OLD.main_category_id = NEW.main_category_id
        AND OLD.subcategory_id IS NOT DISTINCT FROM NEW.subcategory_id
        AND OLD.currency = NEW.currency
        AND OLD.image_urls = NEW.image_urls
        AND OLD.long_description IS NOT DISTINCT FROM NEW.long_description
        AND OLD.option_1_name IS NOT DISTINCT FROM NEW.option_1_name
        AND OLD.option_2_name IS NOT DISTINCT FROM NEW.option_2_name
        AND OLD.sellable_count = NEW.sellable_count
        AND OLD.applied_at = NEW.applied_at
        AND OLD.sealed = FALSE
        AND NEW.sealed = TRUE
    ) THEN
        RAISE EXCEPTION 'catalog publication % can only transition from unsealed to sealed', OLD.id
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*), count(*) FILTER (WHERE sellable)
    INTO configured_variant_count, configured_sellable_count
    FROM catalog_publication_variant
    WHERE publication_id = NEW.id;

    IF configured_variant_count NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'catalog publication % has invalid variant count %',
                NEW.id, configured_variant_count
            USING ERRCODE = '23514';
    END IF;

    IF configured_sellable_count < 1 OR configured_sellable_count <> NEW.sellable_count THEN
        RAISE EXCEPTION 'catalog publication % has invalid sellable count', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.option_1_name IS NULL AND EXISTS (
        SELECT 1
        FROM catalog_publication_variant
        WHERE publication_id = NEW.id
            AND (option_1_value IS NOT NULL OR option_2_value IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'catalog publication % has option values without dimensions', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.option_1_name IS NULL AND configured_variant_count <> 1 THEN
        RAISE EXCEPTION 'catalog publication % without options must have exactly one variant', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.option_1_name IS NOT NULL AND NEW.option_2_name IS NULL AND EXISTS (
        SELECT 1
        FROM catalog_publication_variant
        WHERE publication_id = NEW.id
            AND (option_1_value IS NULL OR option_2_value IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'catalog publication % has invalid one-dimension selections', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.option_2_name IS NOT NULL AND EXISTS (
        SELECT 1
        FROM catalog_publication_variant
        WHERE publication_id = NEW.id
            AND (option_1_value IS NULL OR option_2_value IS NULL)
    ) THEN
        RAISE EXCEPTION 'catalog publication % has invalid two-dimension selections', NEW.id
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM catalog_publication_variant current_variant
        JOIN catalog_variant identity
            ON identity.product_id = current_variant.product_id
            AND identity.id = current_variant.variant_id
        JOIN catalog_publication first_publication
            ON first_publication.product_id = identity.product_id
            AND first_publication.id = identity.first_publication_id
        JOIN catalog_publication_variant first_variant
            ON first_variant.publication_id = first_publication.id
            AND first_variant.variant_id = identity.id
        WHERE current_variant.publication_id = NEW.id
            AND identity.first_publication_id IS NOT NULL
            AND first_publication.sealed
            AND (
                first_publication.option_1_name IS DISTINCT FROM NEW.option_1_name
                OR first_publication.option_2_name IS DISTINCT FROM NEW.option_2_name
                OR first_variant.option_1_value IS DISTINCT FROM current_variant.option_1_value
                OR first_variant.option_2_value IS DISTINCT FROM current_variant.option_2_value
            )
    ) THEN
        RAISE EXCEPTION 'catalog publication % changes a published variant meaning', NEW.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_catalog_publication_header_guard
BEFORE UPDATE OR DELETE ON catalog_publication
FOR EACH ROW
EXECUTE FUNCTION catalog_guard_publication_header();

CREATE FUNCTION catalog_guard_publication_variant_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM catalog_assert_publication_can_change(NEW.publication_id);
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.publication_id <> NEW.publication_id
            OR OLD.product_id <> NEW.product_id
            OR OLD.variant_id <> NEW.variant_id THEN
            RAISE EXCEPTION 'catalog publication variant identity cannot change'
                USING ERRCODE = '23514';
        END IF;

        PERFORM catalog_assert_publication_can_change(OLD.publication_id);
        RETURN NEW;
    END IF;

    PERFORM catalog_assert_publication_can_change(OLD.publication_id);
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_catalog_publication_variant_guard
BEFORE INSERT OR UPDATE OR DELETE ON catalog_publication_variant
FOR EACH ROW
EXECUTE FUNCTION catalog_guard_publication_variant_mutation();

CREATE FUNCTION catalog_guard_variant_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.product_id <> NEW.product_id THEN
            RAISE EXCEPTION 'catalog variant % cannot move products', OLD.id
                USING ERRCODE = '23514';
        END IF;

        IF OLD.first_publication_id IS NOT NULL
            AND OLD.first_publication_id IS DISTINCT FROM NEW.first_publication_id THEN
            RAISE EXCEPTION 'catalog variant % first publication cannot change', OLD.id
                USING ERRCODE = '23514';
        END IF;

        IF OLD.first_publication_id IS NULL AND NEW.first_publication_id IS NOT NULL AND NOT EXISTS (
            SELECT 1
            FROM catalog_publication publication
            JOIN catalog_publication_variant publication_variant
                ON publication_variant.publication_id = publication.id
                AND publication_variant.product_id = publication.product_id
                AND publication_variant.variant_id = NEW.id
            WHERE publication.product_id = NEW.product_id
                AND publication.id = NEW.first_publication_id
                AND publication.sealed
                AND publication_variant.sellable
        ) THEN
            RAISE EXCEPTION 'catalog variant % first publication must contain a sealed sellable snapshot', NEW.id
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_catalog_variant_identity_guard
BEFORE UPDATE ON catalog_variant
FOR EACH ROW
EXECUTE FUNCTION catalog_guard_variant_identity();

CREATE FUNCTION catalog_guard_product_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF OLD.first_published_at IS NOT NULL
            AND OLD.first_published_at IS DISTINCT FROM NEW.first_published_at THEN
            RAISE EXCEPTION 'catalog product % first publication timestamp cannot change', OLD.id
                USING ERRCODE = '23514';
        END IF;

        IF OLD.first_published_at IS NULL
            AND NEW.first_published_at IS NOT NULL
            AND OLD.state <> 'DRAFT' THEN
            RAISE EXCEPTION 'catalog product % first publication must start from draft', OLD.id
                USING ERRCODE = '23514';
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.first_published_at IS NOT NULL
        OR OLD.current_publication_id IS NOT NULL
        OR EXISTS (
            SELECT 1
            FROM catalog_variant
            WHERE product_id = OLD.id
                AND first_publication_id IS NOT NULL
        ) THEN
        RAISE EXCEPTION 'published catalog product % cannot be deleted', OLD.id
            USING ERRCODE = '23514';
    END IF;

    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_catalog_product_identity_guard
BEFORE UPDATE OR DELETE ON catalog_product
FOR EACH ROW
EXECUTE FUNCTION catalog_guard_product_identity();

CREATE FUNCTION catalog_assert_product_committed_state(target_product_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    product_row catalog_product%ROWTYPE;
    draft_count INTEGER;
    draft_variant_count INTEGER;
    unsealed_publication_count INTEGER;
    selected_publication catalog_publication%ROWTYPE;
    listing_row catalog_public_listing%ROWTYPE;
    sellable_variant_count INTEGER;
    representative_amount catalog_inr_amount;
    maximum_amount catalog_inr_amount;
BEGIN
    SELECT *
    INTO product_row
    FROM catalog_product
    WHERE id = target_product_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    SELECT count(*)
    INTO draft_count
    FROM catalog_product_draft
    WHERE product_id = target_product_id;

    SELECT count(*)
    INTO draft_variant_count
    FROM catalog_draft_variant
    WHERE product_id = target_product_id;

    IF draft_count <> 1 OR draft_variant_count NOT BETWEEN 1 AND 100 THEN
        RAISE EXCEPTION 'catalog product % must commit with one draft and 1-100 draft variants',
                target_product_id
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*)
    INTO unsealed_publication_count
    FROM catalog_publication
    WHERE product_id = target_product_id
        AND NOT sealed;

    IF unsealed_publication_count <> 0 THEN
        RAISE EXCEPTION 'catalog product % has unsealed committed publications', target_product_id
            USING ERRCODE = '23514';
    END IF;

    SELECT *
    INTO listing_row
    FROM catalog_public_listing
    WHERE product_id = target_product_id;

    IF product_row.state <> 'PUBLISHED' THEN
        IF FOUND THEN
            RAISE EXCEPTION 'catalog product % cannot have a public listing while %',
                    target_product_id, product_row.state
                USING ERRCODE = '23514';
        END IF;

        RETURN;
    END IF;

    SELECT *
    INTO selected_publication
    FROM catalog_publication
    WHERE product_id = target_product_id
        AND id = product_row.current_publication_id
        AND sealed;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'published catalog product % must select a sealed publication',
                target_product_id
            USING ERRCODE = '23514';
    END IF;

    IF listing_row.product_id IS NULL THEN
        RAISE EXCEPTION 'published catalog product % must have a public listing', target_product_id
            USING ERRCODE = '23514';
    END IF;

    SELECT count(*), min(amount), max(amount)
    INTO sellable_variant_count, representative_amount, maximum_amount
    FROM catalog_publication_variant
    WHERE publication_id = selected_publication.id
        AND sellable;

    IF sellable_variant_count <> selected_publication.sellable_count
        OR sellable_variant_count <> listing_row.sellable_count
        OR listing_row.publication_id <> selected_publication.id
        OR listing_row.name <> selected_publication.name
        OR listing_row.short_description <> selected_publication.short_description
        OR listing_row.main_category_id <> selected_publication.main_category_id
        OR listing_row.subcategory_id IS DISTINCT FROM selected_publication.subcategory_id
        OR listing_row.currency <> selected_publication.currency
        OR listing_row.representative_amount IS DISTINCT FROM representative_amount
        OR listing_row.price_from IS DISTINCT FROM (representative_amount <> maximum_amount) THEN
        RAISE EXCEPTION 'catalog product % public listing does not match selected publication',
                target_product_id
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE FUNCTION catalog_queue_product_committed_state_check()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM catalog_assert_product_committed_state(OLD.id);
        RETURN OLD;
    END IF;

    PERFORM catalog_assert_product_committed_state(NEW.id);
    RETURN NEW;
END;
$$;

CREATE FUNCTION catalog_queue_publication_committed_state_check()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM catalog_assert_product_committed_state(OLD.product_id);
        RETURN OLD;
    END IF;

    PERFORM catalog_assert_product_committed_state(NEW.product_id);
    RETURN NEW;
END;
$$;

CREATE FUNCTION catalog_queue_listing_committed_state_check()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM catalog_assert_product_committed_state(OLD.product_id);
        RETURN OLD;
    END IF;

    PERFORM catalog_assert_product_committed_state(NEW.product_id);
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_catalog_product_committed_state
AFTER INSERT OR UPDATE OR DELETE ON catalog_product
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION catalog_queue_product_committed_state_check();

CREATE CONSTRAINT TRIGGER trg_catalog_publication_committed_state
AFTER INSERT OR UPDATE OR DELETE ON catalog_publication
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION catalog_queue_publication_committed_state_check();

CREATE CONSTRAINT TRIGGER trg_catalog_public_listing_committed_state
AFTER INSERT OR UPDATE OR DELETE ON catalog_public_listing
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION catalog_queue_listing_committed_state_check();
