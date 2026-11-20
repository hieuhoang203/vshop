package vn.vshop.modules.platform.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.platform.dto.request.CreatePolicyRequest;
import vn.vshop.modules.platform.dto.response.PolicyResponse;
import vn.vshop.modules.platform.mapper.PolicyMapper;
import vn.vshop.modules.platform.service.PolicyService;

@RestController
@RequestMapping("/api/v1/admin/policies")
public class AdminPolicyController {
    private final PolicyService service;
    private final PolicyMapper mapper;

    public AdminPolicyController(PolicyService service, PolicyMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<PolicyResponse> list() {
        return service.list().stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse draft(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePolicyRequest request) {
        return mapper.toResponse(
                service.createDraft(jwt.getSubject(), request.policyType(), request.content()));
    }

    @PostMapping("/{id}/approve")
    public PolicyResponse approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(service.approve(jwt.getSubject(), id));
    }
}
