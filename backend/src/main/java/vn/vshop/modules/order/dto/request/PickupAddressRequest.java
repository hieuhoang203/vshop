package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Where the carrier collects parcels. Wire limits only; the service applies the real rules. */
public record PickupAddressRequest(
        @NotBlank @Size(max = 150) String contactName,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 300) String street,
        @NotBlank @Size(max = 150) String ward,
        @NotBlank @Size(max = 150) String district,
        @NotBlank @Size(max = 150) String province) {}
