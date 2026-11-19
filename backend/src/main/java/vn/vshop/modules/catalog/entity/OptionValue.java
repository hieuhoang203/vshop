package vn.vshop.modules.catalog.entity;

import java.util.UUID;

public record OptionValue(UUID id, UUID optionId, String code, String label, int sortOrder) {}
