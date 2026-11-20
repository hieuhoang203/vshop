package vn.vshop.modules.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create body. Wire-level limits only; the service applies the real rules and normalisation. */
public record AddressRequest(
        @Size(max = 60) String label,
        @NotBlank @Size(max = 150) String recipientName,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 300) String street,
        @NotBlank @Size(max = 150) String ward,
        @NotBlank @Size(max = 150) String province,
        Boolean makeDefault) {}
