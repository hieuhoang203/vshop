package vn.vshop.modules.platform.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import vn.vshop.modules.platform.entity.PolicyType;

/** Content is an object of whole numbers; the keys required depend on the type. */
public record CreatePolicyRequest(
        @NotNull PolicyType policyType,
        @NotNull @Size(min = 1, max = 5) Map<String, Long> content) {}
