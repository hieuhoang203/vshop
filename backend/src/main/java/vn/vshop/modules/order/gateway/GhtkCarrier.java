package vn.vshop.modules.order.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GHTK "submit order" API (docs: api.ghtk.vn). Authentication is the {@code Token} and {@code
 * X-Client-Source} headers. Our own order id is sent as {@code order.id}, which GHTK refuses to
 * accept twice, so a repeated request is safe. Money is integer VND; product weights are kilograms.
 * Without credentials {@link #configured()} is false and nothing is sent.
 */
@Component
public class GhtkCarrier implements ShippingCarrier {
    private static final Logger LOG = LoggerFactory.getLogger(GhtkCarrier.class);

    private final String baseUrl;
    private final String token;
    private final String partnerCode;
    private final String webhookToken;
    private final ObjectMapper json;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    GhtkCarrier(
            @Value("${vshop.shipping.ghtk.base-url:https://services-staging.ghtklab.com}")
                    String baseUrl,
            @Value("${vshop.shipping.ghtk.token:}") String token,
            @Value("${vshop.shipping.ghtk.partner-code:}") String partnerCode,
            @Value("${vshop.shipping.ghtk.webhook-token:}") String webhookToken,
            ObjectMapper json) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.partnerCode = partnerCode;
        this.webhookToken = webhookToken;
        this.json = json;
    }

    @Override
    public String code() {
        return "GHTK";
    }

    @Override
    public boolean configured() {
        return !token.isBlank() && !partnerCode.isBlank();
    }

    @Override
    public CreateResult createShipment(CreateRequest r) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", r.partnerOrderId());
        order.put("pick_name", r.pickup().name());
        order.put("pick_money", r.codAmount());
        order.put("pick_address", r.pickup().address());
        order.put("pick_province", r.pickup().province());
        order.put("pick_district", r.pickup().district());
        order.put("pick_ward", r.pickup().ward());
        order.put("pick_tel", r.pickup().phone());
        order.put("name", r.recipient().name());
        order.put("address", r.recipient().address());
        order.put("province", r.recipient().province());
        order.put("ward", r.recipient().ward());
        order.put("hamlet", "Khác");
        order.put("tel", r.recipient().phone());
        order.put("value", r.declaredValue());
        // The buyer already paid the shipping fee to the shop; the courier collects only
        // pick_money.
        order.put("is_freeship", 1);
        order.put("note", r.note());
        List<Map<String, Object>> products = new ArrayList<>();
        for (Item item : r.items()) {
            Map<String, Object> product = new LinkedHashMap<>();
            product.put("name", item.name());
            product.put("weight", item.weightGrams() / 1000.0);
            product.put("quantity", item.quantity());
            products.add(product);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("products", products);
        body.put("order", order);

        Response response;
        try {
            response = send("POST", "/services/shipment/order", json.writeValueAsString(body));
        } catch (RuntimeException e) {
            LOG.warn("GHTK create failed without an answer: {}", e.toString());
            return new CreateResult(
                    Outcome.UNKNOWN, null, 0, 0, "Không nhận được phản hồi từ GHTK.");
        }
        if (response.status() >= 500) {
            return new CreateResult(Outcome.UNKNOWN, null, 0, 0, "GHTK tạm thời lỗi.");
        }
        JsonNode node = response.body();
        if (node.path("success").asBoolean(false)) {
            JsonNode o = node.path("order");
            return new CreateResult(
                    Outcome.CREATED,
                    o.path("label").asText(null),
                    o.path("fee").asLong(0),
                    o.path("insurance_fee").asLong(0),
                    node.path("message").asText(""));
        }
        String message = node.path("message").asText("GHTK từ chối đơn.");
        JsonNode error = node.path("error");
        boolean duplicate =
                "ORDER_ID_EXIST".equals(error.path("code").asText())
                        || node.path("error_code").asInt(0) == 30107;
        if (duplicate) {
            String label = error.path("ghtk_label").asText(null);
            return new CreateResult(Outcome.DUPLICATE, label, 0, 0, message);
        }
        return new CreateResult(Outcome.REJECTED, null, 0, 0, message);
    }

    @Override
    public String lookup(String partnerOrderId) {
        try {
            Response r =
                    send(
                            "GET",
                            "/services/shipment/v2/"
                                    + java.net.URLEncoder.encode(
                                            partnerOrderId, StandardCharsets.UTF_8),
                            null);
            JsonNode order = r.body().path("order");
            return r.body().path("success").asBoolean(false)
                    ? order.path("label_id").asText(null)
                    : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public CancelResult cancel(String trackingNumber) {
        try {
            Response r =
                    send(
                            "POST",
                            "/services/shipment/cancel/"
                                    + java.net.URLEncoder.encode(
                                            trackingNumber, StandardCharsets.UTF_8),
                            "{}");
            boolean ok = r.body().path("success").asBoolean(false);
            String message = r.body().path("message").asText("");
            // 50101: already canceled, which is the state we want.
            boolean already = r.body().path("error_code").asInt(0) == 50101;
            return new CancelResult(ok || already, message);
        } catch (RuntimeException e) {
            return new CancelResult(false, "Không nhận được phản hồi từ GHTK.");
        }
    }

    @Override
    public boolean verifyWebhookToken(String given) {
        return !webhookToken.isBlank()
                && given != null
                && MessageDigest.isEqual(
                        webhookToken.getBytes(StandardCharsets.UTF_8),
                        given.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Event parseWebhook(Map<String, ?> p) {
        String when = text(p, "action_time");
        OffsetDateTime at;
        try {
            at = OffsetDateTime.parse(when);
        } catch (RuntimeException e) {
            at = OffsetDateTime.now(ZoneOffset.UTC);
        }
        int status;
        try {
            status = Integer.parseInt(String.valueOf(p.get("status_id")));
        } catch (NumberFormatException e) {
            status = Integer.MIN_VALUE;
        }
        return new Event(
                text(p, "label_id"),
                text(p, "partner_id"),
                status,
                blankToNull(p.get("reason_code")),
                blankToNull(p.get("reason")),
                at);
    }

    private static String text(Map<String, ?> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String blankToNull(Object value) {
        String s = value == null ? "" : String.valueOf(value).trim();
        return s.isEmpty() || s.equals("null") ? null : s;
    }

    private Response send(String method, String path, String body) {
        try {
            HttpRequest.Builder b =
                    HttpRequest.newBuilder(URI.create(baseUrl + path))
                            .timeout(Duration.ofSeconds(15))
                            .header("Token", token)
                            .header("X-Client-Source", partnerCode)
                            .header("Content-Type", "application/json");
            HttpRequest request =
                    method.equals("GET")
                            ? b.GET().build()
                            : b.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node;
            try {
                node = json.readTree(response.body());
            } catch (RuntimeException e) {
                node = json.createObjectNode();
            }
            return new Response(response.statusCode(), node);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Response(int status, JsonNode body) {}
}
