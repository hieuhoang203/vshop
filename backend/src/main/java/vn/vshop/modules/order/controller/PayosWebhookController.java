package vn.vshop.modules.order.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.service.PaymentService;

/**
 * Payment notifications from payOS. No token: the signature over the {@code data} object is checked
 * by the gateway. payOS only needs a 2xx answer (it also posts a sample payload when the webhook
 * URL is registered, which must be answered 2xx too), so the body only reports whether the
 * notification was accepted. It does not go through the web BFF.
 */
@RestController
@RequestMapping("/api/v1/payments/payos")
public class PayosWebhookController {
    private final PaymentService service;

    public PayosWebhookController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody Map<String, Object> payload) {
        Map<String, String> reply = service.handleCallback("PAYOS", payload);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", !reply.get("RspCode").equals("97"));
        out.put("code", reply.get("RspCode"));
        return out;
    }
}
