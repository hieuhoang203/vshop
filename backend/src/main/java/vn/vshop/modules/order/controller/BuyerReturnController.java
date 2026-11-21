package vn.vshop.modules.order.controller;

import jakarta.validation.Valid;
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
import vn.vshop.modules.order.dto.request.CreateReturnShipmentRequest;
import vn.vshop.modules.order.dto.request.NoteRequest;
import vn.vshop.modules.order.dto.request.OpenReturnRequest;
import vn.vshop.modules.order.dto.request.ShipOrderRequest;
import vn.vshop.modules.order.dto.response.ReturnCaseResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.ReturnCaseService;
import vn.vshop.modules.order.service.ReturnShipmentService;

@RestController
@RequestMapping("/api/v1")
public class BuyerReturnController {
    private final ReturnCaseService service;
    private final ReturnShipmentService returnShipments;

    public BuyerReturnController(ReturnCaseService service, ReturnShipmentService returnShipments) {
        this.service = service;
        this.returnShipments = returnShipments;
    }

    /**
     * Asks the carrier to collect the goods the shop agreed to take back. Repeating it never
     * creates a second parcel.
     */
    @PostMapping("/return-cases/{caseId}/return-shipment")
    public ReturnCaseResponse createReturnShipment(
            @PathVariable UUID caseId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReturnShipmentRequest request) {
        return OrderMapper.toResponse(
                returnShipments.create(jwt.getSubject(), caseId, request.district()));
    }

    /** Opens a return case for a delivered order inside its return window. */
    @PostMapping("/orders/{orderId}/return-case")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnCaseResponse open(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenReturnRequest request) {
        return OrderMapper.toResponse(
                service.open(
                        jwt.getSubject(),
                        orderId,
                        request.reasonCode(),
                        request.preferredResolution(),
                        request.description()));
    }

    @GetMapping("/orders/{orderId}/return-case")
    public ReturnCaseResponse forOrder(
            @PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.forBuyer(jwt.getSubject(), orderId));
    }

    @PostMapping("/return-cases/{id}/accept-offer")
    public ReturnCaseResponse acceptOffer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.acceptOffer(jwt.getSubject(), id));
    }

    @PostMapping("/return-cases/{id}/decline-offer")
    public ReturnCaseResponse declineOffer(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NoteRequest request) {
        return OrderMapper.toResponse(service.declineOffer(jwt.getSubject(), id, request.note()));
    }

    @PostMapping("/return-cases/{id}/escalate")
    public ReturnCaseResponse escalate(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NoteRequest request) {
        return OrderMapper.toResponse(service.escalate(jwt.getSubject(), id, request.note()));
    }

    /** The buyer sends the goods back and types the carrier tracking code. */
    @PostMapping("/return-cases/{id}/ship")
    public ReturnCaseResponse ship(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ShipOrderRequest request) {
        return OrderMapper.toResponse(service.ship(jwt.getSubject(), id, request.trackingCode()));
    }

    @PostMapping("/return-cases/{id}/cancel")
    public ReturnCaseResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrderMapper.toResponse(service.cancel(jwt.getSubject(), id));
    }
}
