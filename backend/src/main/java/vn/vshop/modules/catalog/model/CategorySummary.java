package vn.vshop.modules.catalog.model;

import java.util.UUID;

public record CategorySummary(UUID id, String name, String slug, long count) {}
