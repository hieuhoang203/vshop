package vn.vshop.modules.merchant.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.merchant.dto.request.SubmitShopRequest;
import vn.vshop.modules.merchant.dto.response.ShopResponse;
import vn.vshop.modules.merchant.mapper.ShopMapper;
import vn.vshop.modules.merchant.service.ShopService;

@RestController
@RequestMapping("/api/v1/seller/shops")
public class SellerShopController {
    private final ShopService service;

    public SellerShopController(ShopService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShopResponse> owned(@AuthenticationPrincipal Jwt jwt) {
        return service.owned(jwt.getSubject()).stream().map(ShopMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ShopResponse detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ShopMapper.toResponse(service.ownedShop(id, jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopResponse submit(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubmitShopRequest request) {
        return ShopMapper.toResponse(
                service.submit(jwt.getSubject(), request.name(), request.description()));
    }

    @PutMapping("/{id}")
    public ShopResponse resubmit(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitShopRequest request) {
        return ShopMapper.toResponse(
                service.resubmit(id, jwt.getSubject(), request.name(), request.description()));
    }
}
