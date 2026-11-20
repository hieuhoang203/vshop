package vn.vshop.modules.platform.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.platform.entity.PolicyStatus;
import vn.vshop.modules.platform.entity.PolicyType;
import vn.vshop.modules.platform.entity.PolicyVersion;
import vn.vshop.modules.platform.model.CheckoutPolicies;
import vn.vshop.modules.platform.repository.PolicyRepository;

/**
 * Versioned business parameters with an approval gate. A draft is never used; only the single
 * APPROVED version of a type is read by checkout. Approving retires the previous version, and
 * content of an approved version is never edited (a new version replaces it).
 */
@Service
public class PolicyService {
    private static final TypeReference<Map<String, Long>> NUMBERS = new TypeReference<>() {};
    private static final Map<PolicyType, Map<String, long[]>> RULES =
            Map.of(
                    PolicyType.SHIPPING_FLAT, Map.of("amount", new long[] {0, 1_000_000}),
                    PolicyType.RETURN, Map.of("windowDays", new long[] {0, 365}),
                    PolicyType.COD_LIMITS,
                            Map.of(
                                    "maxOrderAmount", new long[] {1, 100_000_000},
                                    "maxOpenOrders", new long[] {1, 50}),
                    PolicyType.PAYMENT_WINDOW,
                            Map.of(
                                    "paymentMinutes", new long[] {5, 1440},
                                    "reservationBufferMinutes", new long[] {0, 60}));

    private final PolicyRepository policies;
    private final AccountService accounts;
    private final AuditEventService audit;
    private final ObjectMapper json;
    private final Clock clock;

    PolicyService(
            PolicyRepository policies,
            AccountService accounts,
            AuditEventService audit,
            ObjectMapper json,
            Clock clock) {
        this.policies = policies;
        this.accounts = accounts;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public PolicyVersion createDraft(String subject, PolicyType type, Map<String, Long> content) {
        Map<String, Long> valid = validate(type, content);
        UUID creator = accounts.requireActiveAccount(subject);
        try {
            PolicyVersion draft =
                    policies.insertDraft(type, json.writeValueAsString(valid), creator);
            audit.record(subject, "POLICY_DRAFTED", draft.id(), type + " v" + draft.version());
            return draft;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT, "Có người vừa tạo phiên bản chính sách. Thử lại.");
        }
    }

    @Transactional
    public PolicyVersion approve(String subject, UUID id) {
        UUID approver = accounts.requireActiveAccount(subject);
        PolicyVersion policy =
                policies.findForUpdate(id)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.POLICY_NOT_FOUND,
                                                "Không tìm thấy chính sách."));
        if (policy.status() != PolicyStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_POLICY_STATE, "Chỉ bản nháp mới được duyệt.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        policies.retireCurrent(policy.policyType(), now);
        policies.approve(id, approver, now);
        audit.record(subject, "POLICY_APPROVED", id, policy.policyType() + " v" + policy.version());
        return policies.findForUpdate(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PolicyVersion> list() {
        return policies.list();
    }

    /** Return window of the (possibly retired) policy version an order was placed under. */
    public int returnWindowDays(UUID policyId) {
        return (int) value(policies.find(policyId).orElseThrow(), "windowDays");
    }

    public Map<String, Long> content(PolicyVersion policy) {
        return json.readValue(policy.content(), NUMBERS);
    }

    /** Approved policies as typed values; members are null while a type has no approved version. */
    @Transactional(readOnly = true)
    public CheckoutPolicies checkoutPolicies() {
        return new CheckoutPolicies(
                policies.findCurrent(PolicyType.SHIPPING_FLAT)
                        .map(p -> new CheckoutPolicies.ShippingFlat(p.id(), value(p, "amount")))
                        .orElse(null),
                policies.findCurrent(PolicyType.RETURN)
                        .map(
                                p ->
                                        new CheckoutPolicies.ReturnWindow(
                                                p.id(), (int) value(p, "windowDays")))
                        .orElse(null),
                policies.findCurrent(PolicyType.COD_LIMITS)
                        .map(
                                p ->
                                        new CheckoutPolicies.CodLimits(
                                                p.id(),
                                                value(p, "maxOrderAmount"),
                                                (int) value(p, "maxOpenOrders")))
                        .orElse(null),
                policies.findCurrent(PolicyType.PAYMENT_WINDOW)
                        .map(
                                p ->
                                        new CheckoutPolicies.PaymentWindow(
                                                p.id(),
                                                (int) value(p, "paymentMinutes"),
                                                (int) value(p, "reservationBufferMinutes")))
                        .orElse(null));
    }

    private long value(PolicyVersion policy, String key) {
        return content(policy).get(key);
    }

    private Map<String, Long> validate(PolicyType type, Map<String, Long> content) {
        Map<String, long[]> rules = RULES.get(type);
        if (content == null || !content.keySet().equals(rules.keySet())) {
            throw new BusinessException(
                    ErrorCode.INVALID_POLICY_INPUT,
                    "Nội dung chính sách phải có đúng các trường: "
                            + String.join(", ", rules.keySet()));
        }
        Map<String, Long> valid = new LinkedHashMap<>();
        rules.forEach(
                (key, range) -> {
                    Long v = content.get(key);
                    if (v == null || v < range[0] || v > range[1]) {
                        throw new BusinessException(
                                ErrorCode.INVALID_POLICY_INPUT,
                                key + " phải từ " + range[0] + " đến " + range[1] + ".");
                    }
                    valid.put(key, v);
                });
        return valid;
    }
}
