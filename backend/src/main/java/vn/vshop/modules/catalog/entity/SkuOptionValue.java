package vn.vshop.modules.catalog.entity;

import java.util.UUID;

/** Composite identity is (skuId, optionId); composite foreign keys preserve product boundaries. */
public record SkuOptionValue(UUID skuId, UUID productId, UUID optionId, UUID optionValueId) {}
