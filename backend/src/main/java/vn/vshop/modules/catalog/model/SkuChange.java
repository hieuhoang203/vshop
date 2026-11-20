package vn.vshop.modules.catalog.model;

import vn.vshop.modules.catalog.entity.SkuStatus;

public record SkuChange(long price, SkuStatus status, long expectedVersion) {}
