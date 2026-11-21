package vn.vshop.modules.identity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sign-up body. Wire-level limits only; the service applies the real rules. */
public record RegisterRequest(
        @NotNull @Size(max = 120) String fullName,
        @NotNull @Size(max = 254) String email,
        @NotNull @Size(max = 200) String password,
        boolean acceptTerms) {

    /** Keeps the password out of logs and error dumps. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }
}
