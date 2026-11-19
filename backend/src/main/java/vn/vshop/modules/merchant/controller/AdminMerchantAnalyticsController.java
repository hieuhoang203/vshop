package vn.vshop.modules.merchant.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vshop.modules.merchant.dto.response.ShopActivityResponse;
import vn.vshop.modules.merchant.mapper.MerchantAnalyticsMapper;
import vn.vshop.modules.merchant.service.MerchantAnalyticsService;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminMerchantAnalyticsController {
    private final MerchantAnalyticsService service;

    public AdminMerchantAnalyticsController(MerchantAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/shop-activity")
    public ShopActivityResponse activity(
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return MerchantAnalyticsMapper.toResponse(service.report(days));
    }
}
