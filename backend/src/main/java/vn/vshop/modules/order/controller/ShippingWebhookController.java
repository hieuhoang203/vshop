package vn.vshop.modules.order.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.service.ShipmentService;

/**
 * Status reports from GHTK. There is no token: GHTK authenticates only with the secret {@code hash}
 * in the callback URL, which the service compares in constant time. The carrier calls the API
 * directly, not through the web BFF. GHTK retries once on anything but HTTP 200, so a wrong secret
 * is answered 200 with {@code success=false} and logged, and nothing is processed.
 */
@RestController
@RequestMapping("/api/v1/shipping/ghtk")
public class ShippingWebhookController {
    private static final Logger LOG = LoggerFactory.getLogger(ShippingWebhookController.class);

    private final ShipmentService service;

    public ShippingWebhookController(ShipmentService service) {
        this.service = service;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> json(
            @RequestParam(name = "hash", required = false) String hash,
            @RequestBody Map<String, Object> body) {
        return reply(service.handleWebhook(hash, body));
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> form(@RequestParam Map<String, String> parameters) {
        return reply(service.handleWebhook(parameters.get("hash"), parameters));
    }

    private static Map<String, Object> reply(boolean accepted) {
        if (!accepted) {
            LOG.warn("GHTK webhook rejected: wrong or missing secret");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", accepted);
        return out;
    }
}
