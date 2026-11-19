package vn.vshop.modules.catalog.dto.response;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug, long count) {}
