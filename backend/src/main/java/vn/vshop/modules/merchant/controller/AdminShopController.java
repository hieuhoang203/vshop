package vn.vshop.modules.merchant.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.merchant.dto.request.ReviewShopRequest;
import vn.vshop.modules.merchant.dto.response.ShopResponse;
import vn.vshop.modules.merchant.mapper.ShopMapper;
import vn.vshop.modules.merchant.service.ShopService;

@RestController
@RequestMapping("/api/v1/admin/shops")
public class AdminShopController {
    private final ShopService service;

    public AdminShopController(ShopService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShopResponse> queue() {
        return service.reviewQueue().stream().map(ShopMapper::toResponse).toList();
    }

    @PostMapping("/{id}/review")
    public ShopResponse review(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewShopRequest request) {
        return ShopMapper.toResponse(
                service.review(id, jwt.getSubject(), request.decision(), request.reason()));
    }
}
