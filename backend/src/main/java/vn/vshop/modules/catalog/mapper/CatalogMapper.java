package vn.vshop.modules.catalog.mapper;

import vn.vshop.common.model.SearchResult;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.catalog.dto.request.ProductSearchRequest;
import vn.vshop.modules.catalog.dto.response.CategoryResponse;
import vn.vshop.modules.catalog.dto.response.ProductResponse;
import vn.vshop.modules.catalog.model.CatalogSort;
import vn.vshop.modules.catalog.model.CategorySummary;
import vn.vshop.modules.catalog.model.ProductSearchCriteria;
import vn.vshop.modules.catalog.model.ProductSummary;

public final class CatalogMapper {
    private CatalogMapper() {}

    public static ProductSearchCriteria toCriteria(ProductSearchRequest request) {
        return new ProductSearchCriteria(
                request.q(),
                request.category(),
                request.page(),
                request.size(),
                request.minPrice(),
                request.maxPrice(),
                switch (request.sort()) {
                    case "price-asc" -> CatalogSort.PRICE_ASC;
                    case "price-desc" -> CatalogSort.PRICE_DESC;
                    default -> CatalogSort.NEWEST;
                });
    }

    public static CategoryResponse toResponse(CategorySummary category) {
        return new CategoryResponse(
                category.id(), category.name(), category.slug(), category.count());
    }

    public static ProductResponse toResponse(ProductSummary product) {
        return new ProductResponse(
                product.id(),
                product.slug(),
                product.name(),
                product.description(),
                product.categorySlug(),
                product.categoryName(),
                product.shopId(),
                product.shopName(),
                product.price(),
                product.currency(),
                product.stock(),
                product.imageKey());
    }

    public static PageResponse<ProductResponse> toResponse(SearchResult<ProductSummary> result) {
        return new PageResponse<>(
                result.items().stream().map(CatalogMapper::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }
}
