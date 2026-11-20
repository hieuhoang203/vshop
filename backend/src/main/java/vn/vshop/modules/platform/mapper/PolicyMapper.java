package vn.vshop.modules.platform.mapper;

import org.springframework.stereotype.Component;
import vn.vshop.modules.platform.dto.response.PolicyResponse;
import vn.vshop.modules.platform.entity.PolicyVersion;
import vn.vshop.modules.platform.service.PolicyService;

@Component
public class PolicyMapper {
    private final PolicyService service;

    public PolicyMapper(PolicyService service) {
        this.service = service;
    }

    public PolicyResponse toResponse(PolicyVersion p) {
        return new PolicyResponse(
                p.id(),
                p.policyType(),
                p.version(),
                service.content(p),
                p.status(),
                p.effectiveFrom(),
                p.effectiveTo(),
                p.approvedAt(),
                p.createdAt());
    }
}
