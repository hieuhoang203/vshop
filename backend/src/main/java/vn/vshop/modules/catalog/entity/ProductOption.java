package vn.vshop.modules.catalog.entity;

import java.util.UUID;

public record ProductOption(UUID id, UUID productId, String code, String name, int sortOrder) {}
