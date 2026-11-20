package vn.vshop.modules.identity.mapper;

import vn.vshop.modules.identity.dto.request.AddressRequest;
import vn.vshop.modules.identity.dto.request.UpdateAddressRequest;
import vn.vshop.modules.identity.dto.response.AddressResponse;
import vn.vshop.modules.identity.model.AddressDetails;
import vn.vshop.modules.identity.model.AddressInput;

public final class AddressMapper {
    private AddressMapper() {}

    public static AddressInput toInput(AddressRequest request) {
        return new AddressInput(
                request.label(),
                request.recipientName(),
                request.phone(),
                request.street(),
                request.ward(),
                request.province(),
                Boolean.TRUE.equals(request.makeDefault()));
    }

    public static AddressInput toInput(UpdateAddressRequest request) {
        return new AddressInput(
                request.label(),
                request.recipientName(),
                request.phone(),
                request.street(),
                request.ward(),
                request.province(),
                Boolean.TRUE.equals(request.makeDefault()));
    }

    public static AddressResponse toResponse(AddressDetails address) {
        return new AddressResponse(
                address.id(),
                address.label(),
                address.recipientName(),
                address.phone(),
                address.street(),
                address.ward(),
                address.province(),
                address.isDefault(),
                address.version(),
                address.createdAt(),
                address.updatedAt());
    }
}
