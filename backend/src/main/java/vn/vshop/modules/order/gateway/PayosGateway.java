package vn.vshop.modules.order.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * payOS payment links (docs: payos.vn/docs). Requests carry {@code x-client-id} and {@code
 * x-api-key}; the create request and every webhook are signed with HMAC-SHA256 using the channel's
 * checksum key. {@code orderCode} (an integer) is our attempt reference and must be unique. payOS
 * has no sandbox, so anything run with real credentials is real.
 */
@Component
public class PayosGateway implements PaymentGateway {
    private static final Logger LOG = LoggerFactory.getLogger(PayosGateway.class);

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final ObjectMapper json;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    PayosGateway(
            @Value("${vshop.payment.payos.base-url:https://api-merchant.payos.vn}") String baseUrl,
            @Value("${vshop.payment.payos.client-id:}") String clientId,
            @Value("${vshop.payment.payos.api-key:}") String apiKey,
            @Value("${vshop.payment.payos.checksum-key:}") String checksumKey,
            ObjectMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
        this.json = json;
    }

    @Override
    public String provider() {
        return "PAYOS";
    }

    @Override
    public boolean configured() {
        return !clientId.isBlank() && !apiKey.isBlank() && !checksumKey.isBlank();
    }

    @Override
    public SessionResult createSession(Session s) {
        long orderCode;
        try {
            orderCode = Long.parseLong(s.merchantReference());
        } catch (NumberFormatException e) {
            return new SessionResult(Outcome.FAILED, null, null, "Mã tham chiếu không hợp lệ.");
        }
        String description = "VS" + (orderCode % 10_000_000L); // at most 9 characters
        String cancelUrl = s.returnUrl() + "?cancelled=1";
        TreeMap<String, Object> signed = new TreeMap<>();
        signed.put("amount", s.amountVnd());
        signed.put("cancelUrl", cancelUrl);
        signed.put("description", description);
        signed.put("orderCode", orderCode);
        signed.put("returnUrl", s.returnUrl());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", s.amountVnd());
        body.put("description", description);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", s.returnUrl());
        body.put("expiredAt", s.expiresAt().toEpochSecond());
        body.put(
                "items",
                List.of(Map.of("name", "Don hang VShop", "quantity", 1, "price", s.amountVnd())));
        body.put("signature", sign(join(signed)));
        try {
            HttpResponse<String> response =
                    http.send(
                            request("POST", "/v2/payment-requests", json.writeValueAsString(body)),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = parse(response.body());
            if (response.statusCode() == 200 && "00".equals(node.path("code").asText())) {
                JsonNode data = node.path("data");
                return new SessionResult(
                        Outcome.CREATED,
                        data.path("checkoutUrl").asText(null),
                        data.path("paymentLinkId").asText(null),
                        "");
            }
            String desc = node.path("desc").asText("payOS từ chối yêu cầu.");
            LOG.warn("payOS create link answered {} {}", response.statusCode(), desc);
            return new SessionResult(
                    desc.toLowerCase().contains("tồn tại") || desc.toLowerCase().contains("exist")
                            ? Outcome.DUPLICATE
                            : Outcome.FAILED,
                    null,
                    null,
                    desc);
        } catch (java.io.IOException e) {
            LOG.warn("payOS create link failed without an answer: {}", e.toString());
            return new SessionResult(
                    Outcome.FAILED, null, null, "Không nhận được phản hồi từ payOS.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SessionResult(Outcome.FAILED, null, null, "Bị gián đoạn.");
        }
    }

    @Override
    public void cancelSession(String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return;
        }
        try {
            http.send(
                    request(
                            "POST",
                            "/v2/payment-requests/"
                                    + java.net.URLEncoder.encode(
                                            providerReference, StandardCharsets.UTF_8)
                                    + "/cancel",
                            "{\"cancellationReason\":\"Khong su dung\"}"),
                    HttpResponse.BodyHandlers.discarding());
        } catch (java.io.IOException e) {
            LOG.warn("payOS cancel link failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Signature rules: docs "kiểm tra dữ liệu với signature". Over {@code data} only. */
    @Override
    public Callback verify(Map<String, ?> payload) {
        Object dataObject = payload.get("data");
        if (!configured() || !(dataObject instanceof Map<?, ?> data)) {
            return invalid();
        }
        TreeMap<String, Object> sorted = new TreeMap<>();
        data.forEach((k, v) -> sorted.put(String.valueOf(k), v));
        String given = java.util.Objects.toString(payload.get("signature"), "");
        boolean valid =
                MessageDigest.isEqual(
                        sign(join(sorted)).getBytes(StandardCharsets.US_ASCII),
                        given.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        if (!valid) {
            return invalid();
        }
        long amount;
        try {
            amount = Long.parseLong(String.valueOf(sorted.get("amount")));
        } catch (NumberFormatException e) {
            amount = -1;
        }
        String code = String.valueOf(sorted.getOrDefault("code", ""));
        String reference = text(sorted.get("reference"));
        return new Callback(
                true,
                text(sorted.get("orderCode")),
                amount,
                Boolean.TRUE.equals(payload.get("success")) && "00".equals(code),
                reference.isEmpty() ? text(sorted.get("paymentLinkId")) : reference,
                code);
    }

    private static Callback invalid() {
        return new Callback(false, "", -1, false, "", "");
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** {@code key=value} pairs joined with {@code &}, keys sorted, null as empty. */
    private String join(TreeMap<String, Object> sorted) {
        List<String> parts = new ArrayList<>();
        sorted.forEach(
                (k, v) -> {
                    String value;
                    if (v == null || "null".equals(v) || "undefined".equals(v)) {
                        value = "";
                    } else if (v instanceof Map<?, ?> || v instanceof List<?>) {
                        value = json.writeValueAsString(v);
                    } else {
                        value = String.valueOf(v);
                    }
                    parts.add(k + "=" + value);
                });
        return String.join("&", parts);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest request(String method, String path, String body) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException e) {
            return json.createObjectNode();
        }
    }
}
