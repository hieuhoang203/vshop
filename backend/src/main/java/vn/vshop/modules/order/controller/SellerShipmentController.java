package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.dto.request.CreateShipmentRequest;
import vn.vshop.modules.order.dto.request.PickupAddressRequest;
import vn.vshop.modules.order.dto.response.PickupAddressResponse;
import vn.vshop.modules.order.dto.response.SellerOrderResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.ShipmentService;

@RestController
@RequestMapping("/api/v1/seller/shops/{shopId}")
public class SellerShipmentController {
    private final ShipmentService service;

    public SellerShipmentController(ShipmentService service) {
        this.service = service;
    }

    /** The address a carrier collects parcels from; the body is empty while none is set. */
    @GetMapping("/pickup-address")
    public PickupAddressResponse pickup(
            @PathVariable UUID shopId, @AuthenticationPrincipal Jwt jwt) {
        return service.pickup(jwt.getSubject(), shopId)
                .map(SellerShipmentController::toResponse)
                .orElse(null);
    }

    @PutMapping("/pickup-address")
    public PickupAddressResponse savePickup(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PickupAddressRequest request) {
        return toResponse(
                service.savePickup(
                        jwt.getSubject(),
                        shopId,
                        new ShipmentService.Pickup(
                                request.contactName(),
                                request.phone(),
                                request.street(),
                                request.ward(),
                                request.district(),
                                request.province())));
    }

    /** Creates the carrier order for a READY_TO_SHIP order. Repeating it never creates a second. */
    @PostMapping("/orders/{orderId}/shipment")
    public SellerOrderResponse create(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateShipmentRequest request) {
        return OrderMapper.toResponse(
                service.create(jwt.getSubject(), shopId, orderId, request.weightGrams()));
    }

    @PostMapping("/orders/{orderId}/shipment/cancel")
    public SellerOrderResponse cancel(
            @PathVariable UUID shopId,
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.cancel(jwt.getSubject(), shopId, orderId));
    }

    private static PickupAddressResponse toResponse(ShipmentService.Pickup p) {
        return new PickupAddressResponse(
                p.contactName(), p.phone(), p.street(), p.ward(), p.district(), p.province());
    }
}
