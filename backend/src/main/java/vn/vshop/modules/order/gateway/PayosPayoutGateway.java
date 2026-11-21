package vn.vshop.modules.order.gateway;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * payOS payouts (docs: payos.vn/docs, "Chi hộ"). Written from the published description and NOT yet
 * exercised against a real payout channel: that channel needs a separate, verified payOS business
 * account, so this adapter is switched off by default ({@code vshop.payment.payos.payout.enabled})
 * and every unclear answer is reported as UNKNOWN rather than guessed. Before enabling it, check
 * the endpoint paths, the signature rule and the states below against the live documentation and
 * run one small transfer by hand.
 */
@Component
public class PayosPayoutGateway implements PayoutGateway {
    private static final Logger LOG = LoggerFactory.getLogger(PayosPayoutGateway.class);

    private final boolean switchedOn;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final ObjectMapper json;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    PayosPayoutGateway(
            @Value("${vshop.payment.payos.payout.enabled:false}") boolean switchedOn,
            @Value("${vshop.payment.payos.base-url:https://api-merchant.payos.vn}") String baseUrl,
            @Value("${vshop.payment.payos.payout.client-id:}") String clientId,
            @Value("${vshop.payment.payos.payout.api-key:}") String apiKey,
            @Value("${vshop.payment.payos.payout.checksum-key:}") String checksumKey,
            ObjectMapper json) {
        this.switchedOn = switchedOn;
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
    public boolean enabled() {
        return switchedOn && !clientId.isBlank() && !apiKey.isBlank() && !checksumKey.isBlank();
    }

    @Override
    public Result submit(Request r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("referenceId", r.referenceId());
        body.put("amount", r.amountVnd());
        body.put("description", r.description());
        body.put("toBin", r.bankBin());
        body.put("toAccountNumber", r.accountNumber());
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(baseUrl + "/v1/payouts"))
                            .timeout(Duration.ofSeconds(20))
                            .header("x-client-id", clientId)
                            .header("x-api-key", apiKey)
                            .header("x-idempotency-key", r.referenceId())
                            .header("x-signature", sign(body))
                            .header("Content-Type", "application/json")
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            json.writeValueAsString(body)))
                            .build();
            return read(
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("payOS payout request failed, outcome unknown: {}", e.toString());
            return new Result(Outcome.UNKNOWN, null, "Không nhận được phản hồi từ payOS.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.UNKNOWN, null, "Bị gián đoạn.");
        }
    }

    @Override
    public Result query(String referenceId) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            baseUrl
                                                    + "/v1/payouts?referenceId="
                                                    + URLEncoder.encode(
                                                            referenceId, StandardCharsets.UTF_8)))
                            .timeout(Duration.ofSeconds(15))
                            .header("x-client-id", clientId)
                            .header("x-api-key", apiKey)
                            .GET()
                            .build();
            return read(
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("payOS payout query failed: {}", e.toString());
            return new Result(Outcome.UNKNOWN, null, "Không nhận được phản hồi từ payOS.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.UNKNOWN, null, "Bị gián đoạn.");
        }
    }

    /** Maps the provider answer; anything not clearly understood stays UNKNOWN. */
    private Result read(HttpResponse<String> response) {
        if (response.statusCode() >= 500) {
            return new Result(
                    Outcome.UNKNOWN,
                    null,
                    "payOS tạm thời lỗi (HTTP " + response.statusCode() + ").");
        }
        JsonNode node;
        try {
            node = json.readTree(response.body());
        } catch (RuntimeException e) {
            return new Result(Outcome.UNKNOWN, null, "Phản hồi payOS không đọc được.");
        }
        String code = node.path("code").asText("");
        String desc = node.path("desc").asText("");
        if (response.statusCode() / 100 == 4 || (!code.isEmpty() && !code.equals("00"))) {
            // A clear refusal: nothing was sent.
            return new Result(
                    Outcome.FAILED, null, desc.isBlank() ? "payOS từ chối yêu cầu chi." : desc);
        }
        JsonNode data = node.path("data");
        JsonNode payout =
                data.has("payouts") && data.get("payouts").isArray()
                        ? data.get("payouts").path(0)
                        : data;
        if (payout.isMissingNode() || payout.isNull() || payout.isEmpty()) {
            return new Result(
                    Outcome.UNKNOWN, null, "payOS chưa có lệnh chi với mã tham chiếu này.");
        }
        String providerId = payout.path("id").asText(null);
        String state = "";
        String error = "";
        JsonNode transactions = payout.path("transactions");
        if (transactions.isArray() && !transactions.isEmpty()) {
            state = transactions.get(0).path("state").asText("");
            error = transactions.get(0).path("errorMessage").asText("");
        }
        if (state.isEmpty()) {
            state = payout.path("approvalState").asText("");
        }
        return switch (state.toUpperCase()) {
            case "SUCCEEDED" -> new Result(Outcome.SUCCEEDED, providerId, "");
            case "FAILED", "CANCELLED", "CANCELED", "REVERSED", "REJECTED" ->
                    new Result(Outcome.FAILED, providerId, error.isBlank() ? state : error);
            case "RECEIVED", "PROCESSING", "PENDING", "ON_HOLD", "APPROVED", "SUBMITTED" ->
                    new Result(Outcome.PROCESSING, providerId, "");
            default ->
                    new Result(Outcome.UNKNOWN, providerId, "Trạng thái chưa nhận diện: " + state);
        };
    }

    private String sign(Map<String, Object> body) {
        StringBuilder data = new StringBuilder();
        body.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            if (data.length() > 0) {
                                data.append('&');
                            }
                            data.append(e.getKey()).append('=').append(e.getValue());
                        });
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
