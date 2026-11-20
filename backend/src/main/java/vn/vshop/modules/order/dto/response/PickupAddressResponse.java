package vn.vshop.modules.order.dto.response;

public record PickupAddressResponse(
        String contactName,
        String phone,
        String street,
        String ward,
        String district,
        String province) {}
