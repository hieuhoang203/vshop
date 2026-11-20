package vn.vshop.modules.identity.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.security.PiiCipher;
import vn.vshop.modules.identity.entity.Address;
import vn.vshop.modules.identity.model.AddressDetails;
import vn.vshop.modules.identity.model.AddressInput;
import vn.vshop.modules.identity.model.Recipient;
import vn.vshop.modules.identity.model.ShipTo;
import vn.vshop.modules.identity.repository.AddressRepository;

/**
 * The buyer's address book. Only the owner can read or change an address; every lookup is scoped by
 * the account taken from the token. Phone and street are encrypted before they reach the database
 * and are decrypted only for the owner. Province and ward are free text: there is no authoritative
 * administrative list or carrier coverage data yet, so serviceability is not checked here.
 */
@Service
@Transactional
public class AddressService {
    static final int MAX_ADDRESSES = 10;

    /** Vietnamese mobile numbers: 10 digits, 03/05/07/08/09 prefixes. */
    private static final Pattern MOBILE = Pattern.compile("^0[35789]\\d{8}$");

    private final AddressRepository addresses;
    private final AccountService accounts;
    private final PiiCipher cipher;

    public AddressService(AddressRepository addresses, AccountService accounts, PiiCipher cipher) {
        this.addresses = addresses;
        this.accounts = accounts;
        this.cipher = cipher;
    }

    public List<AddressDetails> list(String subject) {
        UUID account = accounts.requireActiveAccount(subject);
        return addresses.findByAccount(account).stream().map(this::open).toList();
    }

    public AddressDetails create(String subject, AddressInput input) {
        UUID account = accounts.requireActiveAccount(subject);
        addresses.lockAccount(account);
        if (addresses.count(account) >= MAX_ADDRESSES) {
            throw new BusinessException(
                    ErrorCode.ADDRESS_LIMIT_REACHED,
                    "Sổ địa chỉ tối đa " + MAX_ADDRESSES + " địa chỉ. Hãy xóa bớt trước khi thêm.");
        }
        boolean first = addresses.count(account) == 0;
        boolean makeDefault = first || input.makeDefault();
        if (makeDefault) {
            addresses.clearDefault(account);
        }
        Address address = seal(UUID.randomUUID(), account, normalise(input), makeDefault, 0);
        addresses.insert(address);
        return open(addresses.find(account, address.id()).orElseThrow());
    }

    public AddressDetails update(
            String subject, UUID id, AddressInput input, long expectedVersion) {
        UUID account = accounts.requireActiveAccount(subject);
        addresses.lockAccount(account);
        Address current = owned(account, id);
        Address replacement = seal(id, account, normalise(input), current.isDefault(), 0);
        if (!addresses.updateContent(replacement, expectedVersion)) {
            throw new BusinessException(
                    ErrorCode.VERSION_CONFLICT,
                    "Địa chỉ đã được thay đổi ở nơi khác. Vui lòng tải lại rồi thử lại.");
        }
        if (input.makeDefault() && !current.isDefault()) {
            addresses.clearDefault(account);
            addresses.markDefault(account, id);
        }
        return open(addresses.find(account, id).orElseThrow());
    }

    public AddressDetails setDefault(String subject, UUID id) {
        UUID account = accounts.requireActiveAccount(subject);
        addresses.lockAccount(account);
        Address current = owned(account, id);
        if (!current.isDefault()) {
            addresses.clearDefault(account);
            addresses.markDefault(account, id);
        }
        return open(addresses.find(account, id).orElseThrow());
    }

    public void delete(String subject, UUID id) {
        UUID account = accounts.requireActiveAccount(subject);
        addresses.lockAccount(account);
        Address current = owned(account, id);
        addresses.delete(account, id);
        // Never leave a buyer with addresses but no default.
        if (current.isDefault()) {
            addresses
                    .findNewest(account)
                    .ifPresent(next -> addresses.markDefault(account, next.id()));
        }
    }

    /**
     * Region and version of an owned address for quotes. A missing address and someone else's
     * address look the same, so ids cannot be probed.
     */
    public ShipTo shipTo(String subject, UUID id) {
        UUID account = accounts.requireActiveAccount(subject);
        Address address = owned(account, id);
        return new ShipTo(address.id(), address.version(), address.province(), address.ward());
    }

    /** Full delivery details of an owned address for an order snapshot. */
    public Recipient recipient(String subject, UUID id) {
        UUID account = accounts.requireActiveAccount(subject);
        AddressDetails d = open(owned(account, id));
        return new Recipient(
                d.id(),
                d.version(),
                d.recipientName(),
                d.phone(),
                d.street(),
                d.ward(),
                d.province());
    }

    /** Current version of an owned address, or empty when it no longer exists. */
    public java.util.Optional<Long> currentVersion(String subject, UUID id) {
        UUID account = accounts.requireActiveAccount(subject);
        return addresses.find(account, id).map(Address::version);
    }

    private Address owned(UUID account, UUID id) {
        return addresses
                .find(account, id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.ADDRESS_NOT_FOUND, "Không tìm thấy địa chỉ."));
    }

    private Address seal(
            UUID id, UUID account, AddressInput clean, boolean isDefault, long version) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new Address(
                id,
                account,
                clean.label(),
                clean.recipientName(),
                cipher.encrypt(clean.phone(), context("phone", id)),
                cipher.encrypt(clean.street(), context("street", id)),
                clean.ward(),
                clean.province(),
                isDefault,
                version,
                now,
                now);
    }

    private AddressDetails open(Address address) {
        return new AddressDetails(
                address.id(),
                address.label(),
                address.recipientName(),
                cipher.decrypt(address.phoneCiphertext(), context("phone", address.id())),
                cipher.decrypt(address.streetCiphertext(), context("street", address.id())),
                address.ward(),
                address.province(),
                address.isDefault(),
                address.version(),
                address.createdAt(),
                address.updatedAt());
    }

    /** Binds each ciphertext to its column and row so it cannot be moved elsewhere. */
    private static String context(String column, UUID id) {
        return "identity.address." + column + ":" + id;
    }

    static AddressInput normalise(AddressInput input) {
        String name = collapse(input.recipientName());
        String street = collapse(input.street());
        String ward = collapse(input.ward());
        String province = collapse(input.province());
        String label = input.label() == null ? null : collapse(input.label());
        if (label != null && label.isEmpty()) {
            label = null;
        }
        String phone = normalisePhone(input.phone());
        require(
                name.length() >= 2 && name.length() <= 100,
                "Tên người nhận cần từ 2 đến 100 ký tự.");
        require(MOBILE.matcher(phone).matches(), "Số điện thoại di động Việt Nam chưa hợp lệ.");
        require(
                street.length() >= 5 && street.length() <= 200,
                "Địa chỉ cụ thể cần từ 5 đến 200 ký tự.");
        require(!ward.isEmpty() && ward.length() <= 100, "Cần nhập phường/xã (tối đa 100 ký tự).");
        require(
                !province.isEmpty() && province.length() <= 100,
                "Cần nhập tỉnh/thành phố (tối đa 100 ký tự).");
        require(label == null || label.length() <= 30, "Nhãn địa chỉ tối đa 30 ký tự.");
        return new AddressInput(label, name, phone, street, ward, province, input.makeDefault());
    }

    /**
     * Accepts 0912345678, 091 234 5678, 091.234.5678 and +84912345678; returns the 0-prefixed form.
     */
    static String normalisePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    private static String collapse(String text) {
        if (text == null) {
            return "";
        }
        require(
                text.chars().noneMatch(Character::isISOControl),
                "Thông tin chứa ký tự không hợp lệ.");
        return text.strip().replaceAll("\\s+", " ");
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(ErrorCode.INVALID_ADDRESS_INPUT, message);
        }
    }
}
