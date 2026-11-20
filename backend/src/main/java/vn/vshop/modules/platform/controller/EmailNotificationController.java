package vn.vshop.modules.platform.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.identity.service.EmailPreferenceService;
import vn.vshop.modules.platform.dto.request.EmailNotificationRequest;
import vn.vshop.modules.platform.dto.response.EmailNotificationResponse;
import vn.vshop.modules.platform.service.EmailDeliveryService;

/**
 * The caller own choice about email. The address comes from the verified email claim of the token,
 * never from the request body.
 */
@RestController
@RequestMapping("/api/v1/me/email-notifications")
public class EmailNotificationController {
    private final EmailPreferenceService preferences;
    private final EmailDeliveryService deliveries;

    public EmailNotificationController(
            EmailPreferenceService preferences, EmailDeliveryService deliveries) {
        this.preferences = preferences;
        this.deliveries = deliveries;
    }

    @GetMapping
    public EmailNotificationResponse get(@AuthenticationPrincipal Jwt jwt) {
        return response(preferences.status(jwt.getSubject()));
    }

    @PutMapping
    public EmailNotificationResponse set(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailNotificationRequest request) {
        var status =
                request.enabled()
                        ? preferences.enable(
                                jwt.getSubject(),
                                jwt.getClaimAsString("email"),
                                jwt.getClaimAsBoolean("email_verified"))
                        : preferences.disable(jwt.getSubject());
        return response(status);
    }

    private EmailNotificationResponse response(EmailPreferenceService.Status s) {
        return new EmailNotificationResponse(s.enabled(), s.maskedEmail(), deliveries.available());
    }
}
