package vn.vshop.modules.identity.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.identity.dto.request.RegisterRequest;
import vn.vshop.modules.identity.service.RegistrationService;

/** Public sign-up for buyers. Answers 201 with no body; the person then signs in. */
@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        registration.register(
                request.fullName(), request.email(), request.password(), request.acceptTerms());
    }
}
