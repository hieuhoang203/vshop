package vn.vshop.modules.identity.controller;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.identity.dto.response.ProfileResponse;

@RestController
public class ProfileController {
    @GetMapping("/api/v1/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        Map<String, Object> realm = jwt.getClaim("realm_access");
        List<String> roles =
                realm != null && realm.get("roles") instanceof List<?> values
                        ? values.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .toList()
                        : List.of();
        return new ProfileResponse(
                jwt.getSubject(),
                username == null ? jwt.getSubject() : username,
                new ProfileResponse.RealmAccess(roles));
    }
}
