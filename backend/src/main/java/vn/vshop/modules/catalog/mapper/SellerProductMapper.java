package vn.vshop.modules.catalog.mapper;

import vn.vshop.common.model.SearchResult;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.catalog.dto.request.CreateProductRequest;
import vn.vshop.modules.catalog.dto.request.UpdateProductRequest;
import vn.vshop.modules.catalog.dto.request.UpdateSkuRequest;
import vn.vshop.modules.catalog.dto.response.SellerProductResponse;
import vn.vshop.modules.catalog.model.NewProduct;
import vn.vshop.modules.catalog.model.ProductContentChange;
import vn.vshop.modules.catalog.model.SellerProduct;
import vn.vshop.modules.catalog.model.SkuChange;

public final class SellerProductMapper {
    private SellerProductMapper() {}

    public static NewProduct toCommand(CreateProductRequest request) {
        return new NewProduct(
                request.name(),
                request.description(),
                request.categoryId(),
                request.imageKey(),
                request.options().stream().map(SellerProductMapper::toCommand).toList(),
                request.skus().stream().map(SellerProductMapper::toCommand).toList());
    }

    private static NewProduct.Option toCommand(CreateProductRequest.OptionRequest option) {
        return new NewProduct.Option(
                option.code(),
                option.name(),
                option.values().stream()
                        .map(value -> new NewProduct.OptionValue(value.code(), value.label()))
                        .toList());
    }

    public static NewProduct.Sku toCommand(CreateProductRequest.SkuRequest sku) {
        return new NewProduct.Sku(sku.sellerSku(), sku.price(), sku.optionValues());
    }

    public static ProductContentChange toCommand(UpdateProductRequest request) {
        return new ProductContentChange(
                request.name(),
                request.description(),
                request.categoryId(),
                request.imageKey(),
                request.expectedVersion());
    }

    public static SkuChange toCommand(UpdateSkuRequest request) {
        return new SkuChange(request.price(), request.status(), request.expectedVersion());
    }

    public static PageResponse<SellerProductResponse> toResponse(
            SearchResult<SellerProduct> result) {
        return new PageResponse<>(
                result.items().stream().map(SellerProductMapper::toResponse).toList(),
                result.total(),
                result.page(),
                result.size());
    }

    public static SellerProductResponse toResponse(SellerProduct view) {
        var product = view.product();
        return new SellerProductResponse(
                product.id(),
                product.shopId(),
                view.shopName(),
                product.categoryId(),
                product.slug(),
                product.name(),
                product.description(),
                product.imageKey(),
                product.status(),
                product.rejectionReason(),
                product.version(),
                product.createdAt(),
                product.updatedAt(),
                view.options().stream().map(SellerProductMapper::toResponse).toList(),
                view.skus().stream().map(SellerProductMapper::toResponse).toList());
    }

    private static SellerProductResponse.OptionResponse toResponse(SellerProduct.Option option) {
        return new SellerProductResponse.OptionResponse(
                option.option().code(),
                option.option().name(),
                option.values().stream()
                        .map(
                                value ->
                                        new SellerProductResponse.ValueResponse(
                                                value.code(), value.label()))
                        .toList());
    }

    private static SellerProductResponse.SkuResponse toResponse(SellerProduct.Sku entry) {
        return new SellerProductResponse.SkuResponse(
                entry.sku().id(),
                entry.sku().sellerSku(),
                entry.sku().price(),
                entry.sku().currency(),
                entry.sku().status(),
                entry.sku().version(),
                entry.optionValues(),
                entry.available());
    }
}
