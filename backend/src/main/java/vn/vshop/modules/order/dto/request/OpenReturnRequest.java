package vn.vshop.modules.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason codes and resolutions are validated by the service; see docs/24 section 2. */
public record OpenReturnRequest(
        @NotBlank @Size(max = 30) String reasonCode,
        @NotBlank @Size(max = 20) String preferredResolution,
        @NotBlank @Size(max = 1100) String description) {}
