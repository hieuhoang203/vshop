package vn.vshop.modules.platform.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.common.web.dto.PageResponse;
import vn.vshop.modules.platform.dto.response.NotificationResponse;
import vn.vshop.modules.platform.dto.response.UnreadCountResponse;
import vn.vshop.modules.platform.service.NotificationService;

/** The caller own notifications only: every query is scoped by the account in the token. */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        List<NotificationResponse> items =
                service.list(jwt.getSubject(), unread, page, size).stream()
                        .map(
                                n ->
                                        new NotificationResponse(
                                                n.id(),
                                                n.kind(),
                                                n.title(),
                                                n.body(),
                                                n.link(),
                                                n.readAt() != null,
                                                n.createdAt()))
                        .toList();
        return new PageResponse<>(items, service.count(jwt.getSubject(), unread), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unread(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(service.count(jwt.getSubject(), true));
    }

    @PostMapping("/{id}/read")
    public UnreadCountResponse read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(jwt.getSubject(), id);
        return new UnreadCountResponse(service.count(jwt.getSubject(), true));
    }

    @PostMapping("/read-all")
    public UnreadCountResponse readAll(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(jwt.getSubject());
        return new UnreadCountResponse(0);
    }
}
