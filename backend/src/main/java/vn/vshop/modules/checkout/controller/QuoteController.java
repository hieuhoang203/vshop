package vn.vshop.modules.checkout.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.checkout.dto.request.CreateQuoteRequest;
import vn.vshop.modules.checkout.dto.response.QuoteResponse;
import vn.vshop.modules.checkout.mapper.CheckoutMapper;
import vn.vshop.modules.checkout.service.QuoteService;

@RestController
@RequestMapping("/api/v1/checkout/quotes")
public class QuoteController {
    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    /** Calculates a quote from the caller's current cart. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) CreateQuoteRequest request) {
        return CheckoutMapper.toResponse(
                service.create(jwt.getSubject(), request == null ? null : request.addressId()));
    }

    @GetMapping("/{id}")
    public QuoteResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return CheckoutMapper.toResponse(service.get(jwt.getSubject(), id));
    }
}
