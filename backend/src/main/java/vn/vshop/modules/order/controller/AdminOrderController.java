package vn.vshop.modules.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.order.dto.response.OverdueOrdersResponse;
import vn.vshop.modules.order.mapper.OrderMapper;
import vn.vshop.modules.order.service.OrderFulfillmentService;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final OrderFulfillmentService service;

    public AdminOrderController(OrderFulfillmentService service) {
        this.service = service;
    }

    /** Support queue: orders no shop has confirmed within the approved deadline. */
    @GetMapping("/overdue")
    public OverdueOrdersResponse overdue() {
        return OrderMapper.toResponse(service.overdue());
    }
}
