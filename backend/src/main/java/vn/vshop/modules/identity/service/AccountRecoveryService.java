package vn.vshop.modules.identity.service;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.AttemptLimiter;
import vn.vshop.modules.identity.gateway.IdentityProvisioner;

/**
 * Password reset and e-mail verification. Both only ask the identity provider to send a link; the
 * link, the token inside it and the new password never pass through this application. Sending is
 * limited per address (and overall) because each call costs a real e-mail.
 */
@Service
public class AccountRecoveryService {
    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@<>\",;]{1,64}@[^\\s@<>\",;]{1,190}\\.[A-Za-z]{2,}$");
    private static final Duration HOUR = Duration.ofHours(1);

    private final IdentityProvisioner identities;
    private final AttemptLimiter limiter;

    AccountRecoveryService(IdentityProvisioner identities, AttemptLimiter limiter) {
        this.identities = identities;
        this.limiter = limiter;
    }

    /** Always looks successful for well-formed input, whether or not the address is registered. */
    public void requestPasswordReset(String email) {
        String address = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (address.length() > 254 || !EMAIL.matcher(address).matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REGISTRATION_INPUT, "Địa chỉ email không hợp lệ.");
        }
        if (!limiter.tryAcquire("reset:" + address, 3, HOUR)
                || !limiter.tryAcquire("reset:all", 300, HOUR)) {
            throw tooMany();
        }
        if (!identities.configured() || !identities.sendPasswordReset(address)) {
            throw unavailable();
        }
    }

    /** Sends the signed-in person a link to confirm the e-mail address of their account. */
    public void requestEmailVerification(String subject) {
        try {
            UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw unavailable();
        }
        if (!limiter.tryAcquire("verify:" + subject, 3, HOUR)) {
            throw tooMany();
        }
        if (!identities.configured() || !identities.sendVerificationEmail(subject)) {
            throw unavailable();
        }
    }

    private static BusinessException tooMany() {
        return new BusinessException(
                ErrorCode.TOO_MANY_REQUESTS,
                "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau ít phút.");
    }

    private static BusinessException unavailable() {
        return new BusinessException(
                ErrorCode.EMAIL_DELIVERY_UNAVAILABLE,
                "Chưa gửi được email lúc này. Vui lòng thử lại sau.");
    }
}
