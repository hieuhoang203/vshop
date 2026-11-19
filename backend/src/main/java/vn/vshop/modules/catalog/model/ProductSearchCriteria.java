package vn.vshop.modules.catalog.model;

public record ProductSearchCriteria(
        String query,
        String category,
        int page,
        int size,
        long minPrice,
        long maxPrice,
        CatalogSort sort) {}
