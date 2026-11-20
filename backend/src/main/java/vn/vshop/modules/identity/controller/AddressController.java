package vn.vshop.modules.identity.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.identity.dto.request.AddressRequest;
import vn.vshop.modules.identity.dto.request.UpdateAddressRequest;
import vn.vshop.modules.identity.dto.response.AddressResponse;
import vn.vshop.modules.identity.mapper.AddressMapper;
import vn.vshop.modules.identity.service.AddressService;

/** The caller's own address book; the account always comes from the token. */
@RestController
@RequestMapping("/api/v1/me/addresses")
public class AddressController {
    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt.getSubject()).stream().map(AddressMapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return AddressMapper.toResponse(
                service.create(jwt.getSubject(), AddressMapper.toInput(request)));
    }

    @PutMapping("/{id}")
    public AddressResponse update(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateAddressRequest request) {
        return AddressMapper.toResponse(
                service.update(
                        jwt.getSubject(),
                        id,
                        AddressMapper.toInput(request),
                        request.expectedVersion()));
    }

    @PutMapping("/{id}/default")
    public AddressResponse makeDefault(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return AddressMapper.toResponse(service.setDefault(jwt.getSubject(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(jwt.getSubject(), id);
    }
}
