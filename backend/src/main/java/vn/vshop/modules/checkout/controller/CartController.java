package vn.vshop.modules.checkout.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.checkout.dto.request.CartLineRequest;
import vn.vshop.modules.checkout.dto.request.SetQuantityRequest;
import vn.vshop.modules.checkout.dto.response.CartResponse;
import vn.vshop.modules.checkout.mapper.CheckoutMapper;
import vn.vshop.modules.checkout.service.CartService;

/** The caller's own cart; the account comes from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public CartResponse view(@AuthenticationPrincipal Jwt jwt) {
        return CheckoutMapper.toResponse(service.view(jwt.getSubject()));
    }

    @PostMapping("/lines")
    public CartResponse add(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartLineRequest request) {
        return CheckoutMapper.toResponse(
                service.addLine(jwt.getSubject(), request.skuId(), request.quantity()));
    }

    @PutMapping("/lines/{skuId}")
    public CartResponse setQuantity(
            @PathVariable UUID skuId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SetQuantityRequest request) {
        return CheckoutMapper.toResponse(
                service.setQuantity(jwt.getSubject(), skuId, request.quantity()));
    }

    @DeleteMapping("/lines/{skuId}")
    public CartResponse remove(@PathVariable UUID skuId, @AuthenticationPrincipal Jwt jwt) {
        return CheckoutMapper.toResponse(service.removeLine(jwt.getSubject(), skuId));
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal Jwt jwt) {
        return CheckoutMapper.toResponse(service.clear(jwt.getSubject()));
    }
}
