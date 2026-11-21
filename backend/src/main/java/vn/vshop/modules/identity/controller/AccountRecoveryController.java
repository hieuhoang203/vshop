package vn.vshop.modules.identity.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.identity.dto.request.ForgotPasswordRequest;
import vn.vshop.modules.identity.service.AccountRecoveryService;

/** Password reset (public) and e-mail verification (signed in). Both answer 202 with no body. */
@RestController
public class AccountRecoveryController {
    private final AccountRecoveryService recovery;

    public AccountRecoveryController(AccountRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/api/v1/auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        recovery.requestPasswordReset(request.email());
    }

    @PostMapping("/api/v1/me/email-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void verifyEmail(@AuthenticationPrincipal Jwt jwt) {
        recovery.requestEmailVerification(jwt.getSubject());
    }
}
