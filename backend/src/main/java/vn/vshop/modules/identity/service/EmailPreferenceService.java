package vn.vshop.modules.identity.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.identity.repository.NotificationEmailRepository;

/**
 * Whether a person receives notifications by email. Opt-in: the address is taken from the verified
 * email of their sign-in identity (never typed into a form) and stored encrypted. It is decrypted
 * only to send mail, and read again at send time so turning the channel off takes effect at once.
 */
@Service
public class EmailPreferenceService {
    private static final Pattern EMAIL =
            Pattern.compile("^[^\\s@<>\",;]{1,64}@[^\\s@<>\",;]{1,190}\\.[A-Za-z]{2,}$");

    private final NotificationEmailRepository emails;
    private final AccountService accounts;
    private final PiiCipher cipher;

    EmailPreferenceService(
            NotificationEmailRepository emails, AccountService accounts, PiiCipher cipher) {
        this.emails = emails;
        this.accounts = accounts;
        this.cipher = cipher;
    }

    /** {@code maskedEmail} is null until the channel was turned on once. */
    public record Status(boolean enabled, String maskedEmail) {}

    @Transactional
    public Status status(String subject) {
        UUID account = accounts.requireActiveAccount(subject);
        return emails.find(account)
                .map(r -> new Status(r.enabled(), mask(decrypt(account, r.emailCiphertext()))))
                .orElse(new Status(false, null));
    }

    /**
     * Turns email on using the identity claims of the token. A missing or unverified address is
     * refused: mail is never sent to an address nobody proved they own.
     */
    @Transactional
    public Status enable(String subject, String email, Boolean emailVerified) {
        String address = email == null ? "" : email.trim();
        if (address.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Tài khoản đăng nhập của bạn chưa có địa chỉ email.");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new BusinessException(
                    ErrorCode.EMAIL_NOT_VERIFIED,
                    "Email của tài khoản chưa được xác minh nên chưa thể gửi thông báo.");
        }
        if (address.length() > 254 || !EMAIL.matcher(address).matches()) {
            throw new BusinessException(
                    ErrorCode.EMAIL_NOT_VERIFIED, "Địa chỉ email của tài khoản không hợp lệ.");
        }
        UUID account = accounts.requireActiveAccount(subject);
        emails.enable(account, cipher.encrypt(address, context(account)));
        return new Status(true, mask(address));
    }

    @Transactional
    public Status disable(String subject) {
        UUID account = accounts.requireActiveAccount(subject);
        emails.disable(account);
        return status(subject);
    }

    /**
     * The address to send to, only while the channel is on. For the mail sender, never for APIs.
     */
    @Transactional
    public Optional<String> emailFor(UUID accountId) {
        return emails.find(accountId)
                .filter(NotificationEmailRepository.Row::enabled)
                .map(r -> decrypt(accountId, r.emailCiphertext()));
    }

    private String decrypt(UUID account, String envelope) {
        return cipher.decrypt(envelope, context(account));
    }

    private static String context(UUID account) {
        return "identity.notification_email.email:" + account;
    }

    /** {@code h***@gmail.com}: enough to recognise the address, not enough to reuse it. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1
                ? "***" + email.substring(Math.max(at, 0))
                : email.charAt(0) + "***" + email.substring(at);
    }
}
