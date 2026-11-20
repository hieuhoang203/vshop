package vn.vshop.modules.order.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.service.PaymentService;

/**
 * Server-to-server callback (IPN) from VNPay. It has no token: the signature and the amount are
 * checked by the service. It does not go through the web BFF; the provider calls the API directly
 * over HTTPS. The reply format ({@code RspCode}/{@code Message}) is the one VNPay defines.
 */
@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class PaymentCallbackController {
    private final PaymentService service;

    public PaymentCallbackController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> parameters) {
        return service.handleCallback(parameters);
    }
}
