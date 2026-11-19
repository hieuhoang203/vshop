package vn.vshop.modules.identity.dto.response;

import java.util.List;

public record ProfileResponse(String subject, String username, RealmAccess realmAccess) {
    public record RealmAccess(List<String> roles) {}
}
