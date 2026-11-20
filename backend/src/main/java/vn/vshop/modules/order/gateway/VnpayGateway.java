package vn.vshop.modules.order.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * VNPay "pay" API 2.1.0: HMAC-SHA512 over the sorted, URL-encoded parameters. Amounts are sent
 * times 100 and dates are Vietnam time (GMT+7). The credentials come from configuration; without
 * them {@link #configured()} is false.
 */
@Component
public class VnpayGateway implements PaymentGateway {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;

    VnpayGateway(
            @Value("${vshop.payment.vnpay.tmn-code:}") String tmnCode,
            @Value("${vshop.payment.vnpay.hash-secret:}") String hashSecret,
            @Value(
                            "${vshop.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
                    String payUrl) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
    }

    @Override
    public String provider() {
        return "VNPAY";
    }

    @Override
    public boolean configured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank();
    }

    @Override
    public String paymentUrl(Session s) {
        TreeMap<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", Long.toString(Math.multiplyExact(s.amountVnd(), 100L)));
        p.put("vnp_CreateDate", STAMP.format(s.createdAt().atZoneSameInstant(VIETNAM)));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_IpAddr", s.clientIp());
        p.put("vnp_Locale", "vn");
        p.put("vnp_OrderInfo", s.orderInfo());
        p.put("vnp_OrderType", "other");
        p.put("vnp_ReturnUrl", s.returnUrl());
        p.put("vnp_ExpireDate", STAMP.format(s.expiresAt().atZoneSameInstant(VIETNAM)));
        p.put("vnp_TxnRef", s.merchantReference());
        StringBuilder query = new StringBuilder();
        p.forEach(
                (k, v) ->
                        query.append(query.isEmpty() ? "" : "&")
                                .append(k)
                                .append('=')
                                .append(enc(v)));
        return payUrl + "?" + query + "&vnp_SecureHash=" + sign(p);
    }

    @Override
    public Callback verify(Map<String, String> parameters) {
        TreeMap<String, String> p = new TreeMap<>();
        parameters.forEach(
                (k, v) -> {
                    if (k.startsWith("vnp_")
                            && !k.equals("vnp_SecureHash")
                            && !k.equals("vnp_SecureHashType")) {
                        p.put(k, v);
                    }
                });
        String given = parameters.getOrDefault("vnp_SecureHash", "");
        boolean valid =
                configured()
                        && !given.isEmpty()
                        && MessageDigest.isEqual(
                                sign(p).getBytes(StandardCharsets.US_ASCII),
                                given.toLowerCase().getBytes(StandardCharsets.US_ASCII))
                        && tmnCode.equals(p.get("vnp_TmnCode"));
        long amount = -1;
        try {
            long raw = Long.parseLong(p.getOrDefault("vnp_Amount", ""));
            amount = raw % 100 == 0 ? raw / 100 : -1;
        } catch (NumberFormatException ignored) {
            // stays -1: callers treat it as an amount that cannot match
        }
        String code = p.getOrDefault("vnp_ResponseCode", "");
        return new Callback(
                valid,
                p.getOrDefault("vnp_TxnRef", ""),
                amount,
                "00".equals(code) && "00".equals(p.get("vnp_TransactionStatus")),
                p.getOrDefault("vnp_TransactionNo", ""),
                code);
    }

    private String sign(TreeMap<String, String> p) {
        StringBuilder data = new StringBuilder();
        p.forEach(
                (k, v) -> {
                    if (v != null && !v.isEmpty()) {
                        data.append(data.isEmpty() ? "" : "&").append(k).append('=').append(enc(v));
                    }
                });
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
