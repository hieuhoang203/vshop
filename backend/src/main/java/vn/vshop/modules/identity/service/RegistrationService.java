package vn.vshop.modules.identity.service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.AttemptLimiter;
import vn.vshop.modules.identity.gateway.IdentityProvisioner;

/**
 * Self-service sign-up. Validates the form, then asks the identity provider to create the buyer.
 * The local account row is created lazily at the first authenticated request, so nothing is stored
 * here and no password is kept or logged. Sellers and administrators are never created this way.
 */
@Service
public class RegistrationService {
    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@<>\",;]{1,64}@[^\\s@<>\",;]{1,190}\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L}\\p{M} .'\\-]{1,59}$");
    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX = 64;

    private final IdentityProvisioner identities;
    private final AttemptLimiter limiter;

    RegistrationService(IdentityProvisioner identities, AttemptLimiter limiter) {
        this.identities = identities;
        this.limiter = limiter;
    }

    /** Not transactional on purpose: it calls another system and writes no local data. */
    public void register(String fullName, String email, String password, boolean acceptedTerms) {
        String name =
                fullName == null
                        ? ""
                        : Normalizer.normalize(fullName, Normalizer.Form.NFC)
                                .trim()
                                .replaceAll("\\s+", " ");
        String address = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String secret = password == null ? "" : password;
        if (!NAME.matcher(name).matches()) {
            throw invalid("Họ tên cần 2–60 ký tự chữ, không chứa số hoặc ký tự đặc biệt.");
        }
        if (address.length() > 254 || !EMAIL.matcher(address).matches()) {
            throw invalid("Địa chỉ email không hợp lệ.");
        }
        String weak = passwordProblem(secret, address);
        if (weak != null) {
            throw invalid(weak);
        }
        if (!acceptedTerms) {
            throw invalid("Bạn cần đồng ý với điều khoản sử dụng để tạo tài khoản.");
        }
        // Second line of defence for callers that skip the web BFF (the mobile app).
        if (!limiter.tryAcquire("register:" + address, 5, Duration.ofHours(1))
                || !limiter.tryAcquire("register:all", 600, Duration.ofHours(1))) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS,
                    "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ít phút.");
        }
        if (!identities.configured()) {
            throw new BusinessException(
                    ErrorCode.REGISTRATION_UNAVAILABLE,
                    "Đăng ký tài khoản tạm thời chưa khả dụng. Vui lòng thử lại sau.");
        }
        // Vietnamese order: the given name is the last word, the rest is the family part. The
        // identity provider builds the display name as first + " " + last.
        int cut = name.lastIndexOf(' ');
        String first = cut < 0 ? name : name.substring(0, cut);
        String last = cut < 0 ? "" : name.substring(cut + 1);
        switch (identities.createBuyer(
                new IdentityProvisioner.NewIdentity(address, first, last, secret))) {
            case CREATED -> {}
            case EMAIL_TAKEN ->
                    throw new BusinessException(
                            ErrorCode.EMAIL_ALREADY_REGISTERED,
                            "Email này đã được đăng ký. Hãy đăng nhập hoặc dùng email khác.");
            case PASSWORD_REJECTED ->
                    throw invalid(
                            "Mật khẩu chưa đáp ứng chính sách bảo mật. Hãy chọn mật khẩu khác.");
            case UNAVAILABLE ->
                    throw new BusinessException(
                            ErrorCode.REGISTRATION_UNAVAILABLE,
                            "Không thể tạo tài khoản lúc này. Vui lòng thử lại sau.");
        }
    }

    /** Null when the password is acceptable. */
    static String passwordProblem(String password, String email) {
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            return "Mật khẩu cần từ 8 đến 64 ký tự.";
        }
        if (!password.equals(password.strip())) {
            return "Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.";
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "Mật khẩu cần có cả chữ cái và chữ số.";
        }
        String local = email.substring(0, Math.max(email.indexOf('@'), 0));
        if (password.equalsIgnoreCase(email)
                || (local.length() >= 4 && password.equalsIgnoreCase(local))) {
            return "Mật khẩu không được trùng với email.";
        }
        return null;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_REGISTRATION_INPUT, message);
    }
}
