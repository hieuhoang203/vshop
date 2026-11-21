package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodSettledRequest(@NotBlank @Size(max = 100) String reference) {}
