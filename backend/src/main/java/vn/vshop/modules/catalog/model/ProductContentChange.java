package vn.vshop.modules.catalog.model;

import java.util.UUID;

public record ProductContentChange(
        String name, String description, UUID categoryId, String imageKey, long expectedVersion) {}
