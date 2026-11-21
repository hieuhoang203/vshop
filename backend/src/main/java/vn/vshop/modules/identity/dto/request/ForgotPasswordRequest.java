package vn.vshop.modules.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotNull @Size(max = 254) String email) {}
