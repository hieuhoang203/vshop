package vn.vshop.modules.catalog.model;

import java.util.UUID;

/** A buyable SKU of a visible product. {@code variantLabel} is null for single-SKU products. */
public record PublicSku(UUID id, String variantLabel, long price, long available) {}
