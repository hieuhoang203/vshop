package vn.vshop.modules.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.catalog.dto.request.ProductSearchRequest;
import vn.vshop.modules.catalog.dto.response.CategoryResponse;
import vn.vshop.modules.catalog.dto.response.ProductResponse;
import vn.vshop.modules.catalog.dto.response.PublicSkuResponse;
import vn.vshop.modules.catalog.mapper.CatalogMapper;
import vn.vshop.modules.catalog.service.CatalogService;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return service.categories().stream().map(CatalogMapper::toResponse).toList();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @Valid @ModelAttribute ProductSearchRequest request) {
        return CatalogMapper.toResponse(service.search(CatalogMapper.toCriteria(request)));
    }

    @GetMapping("/products/{slug}")
    public ProductResponse detail(@PathVariable @Size(max = 160) String slug) {
        return CatalogMapper.toResponse(service.detail(slug));
    }

    @GetMapping("/products/{slug}/skus")
    public List<PublicSkuResponse> skus(@PathVariable @Size(max = 160) String slug) {
        return service.skus(slug).stream()
                .map(
                        sku ->
                                new PublicSkuResponse(
                                        sku.id(), sku.variantLabel(), sku.price(), sku.available()))
                .toList();
    }
}
