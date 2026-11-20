package vn.vshop.modules.catalog.service;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.common.model.SearchResult;
import vn.vshop.modules.catalog.entity.OptionValue;
import vn.vshop.modules.catalog.entity.Product;
import vn.vshop.modules.catalog.entity.ProductOption;
import vn.vshop.modules.catalog.entity.ProductSku;
import vn.vshop.modules.catalog.entity.ProductStatus;
import vn.vshop.modules.catalog.entity.SkuOptionValue;
import vn.vshop.modules.catalog.entity.SkuStatus;
import vn.vshop.modules.catalog.model.NewProduct;
import vn.vshop.modules.catalog.model.ProductContentChange;
import vn.vshop.modules.catalog.model.SellerProduct;
import vn.vshop.modules.catalog.model.SkuChange;
import vn.vshop.modules.catalog.repository.SellerCatalogRepository;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.merchant.entity.Shop;
import vn.vshop.modules.merchant.entity.ShopStatus;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.platform.service.AuditEventService;

/**
 * Seller commands for product content, variant dimensions, SKU price and sale status. Stock
 * quantities are owned by the inventory module and are not written here.
 */
@Service
@Transactional(readOnly = true)
public class SellerCatalogService {
    /** Images are currently bundled fixtures; replace with media assets once uploads exist. */
    static final Set<String> IMAGE_KEYS =
            Set.of("lamp", "tote", "mug", "headphones", "bottle", "notebook");

    static final int MAX_OPTIONS = 3;
    static final int MAX_VALUES_PER_OPTION = 30;
    static final int MAX_SKUS_PER_PRODUCT = 100;
    static final long MAX_PRICE_VND = 1_000_000_000L;
    private static final int SIGNATURE_LIMIT = 255;

    private final SellerCatalogRepository catalog;
    private final ShopService shops;
    private final AccountService accounts;
    private final AuditEventService events;

    public SellerCatalogService(
            SellerCatalogRepository catalog,
            ShopService shops,
            AccountService accounts,
            AuditEventService events) {
        this.catalog = catalog;
        this.shops = shops;
        this.accounts = accounts;
        this.events = events;
    }

    public SearchResult<SellerProduct> list(String subject, UUID shopId, int page, int size) {
        shops.ownedShop(shopId, subject);
        return catalog.listProducts(shopId, page, size);
    }

    public SellerProduct get(String subject, UUID shopId, UUID productId) {
        shops.ownedShop(shopId, subject);
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    /** Used by the inventory module to confirm a SKU belongs to the shop; no ownership check. */
    public ProductSku shopSku(UUID shopId, UUID skuId) {
        return catalog.findShopSku(skuId, shopId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy SKU."));
    }

    @Transactional
    public SellerProduct create(String subject, UUID shopId, NewProduct command) {
        requireWritableShop(subject, shopId);
        requireCategory(command.categoryId());
        requireImage(command.imageKey());
        Map<String, Map<String, UUID>> optionIds = new HashMap<>();
        List<String> optionOrder = validateOptions(command.options());
        validateSkus(command.skus(), command.options(), optionOrder);

        UUID productId = UUID.randomUUID();
        catalog.insertProduct(
                new Product(
                        productId,
                        shopId,
                        command.categoryId(),
                        slugFor(command.name(), productId),
                        command.name().strip(),
                        command.description().strip(),
                        command.imageKey(),
                        ProductStatus.DRAFT,
                        0,
                        null,
                        null));
        Map<String, UUID> optionIdByCode = new HashMap<>();
        int optionSort = 0;
        for (NewProduct.Option option : command.options()) {
            UUID optionId = UUID.randomUUID();
            optionIdByCode.put(option.code(), optionId);
            catalog.insertOption(
                    new ProductOption(
                            optionId,
                            productId,
                            option.code(),
                            option.name().strip(),
                            optionSort++));
            Map<String, UUID> valueIds = new HashMap<>();
            int valueSort = 0;
            for (NewProduct.OptionValue value : option.values()) {
                UUID valueId = UUID.randomUUID();
                valueIds.put(value.code(), valueId);
                catalog.insertOptionValue(
                        new OptionValue(
                                valueId,
                                optionId,
                                value.code(),
                                value.label().strip(),
                                valueSort++));
            }
            optionIds.put(option.code(), valueIds);
        }
        for (NewProduct.Sku sku : command.skus()) {
            insertSku(shopId, productId, sku, optionOrder, optionIdByCode, optionIds);
        }
        events.record(subject, "ProductCreated", shopId, "product " + productId + " drafted");
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    @Transactional
    public SellerProduct updateContent(
            String subject, UUID shopId, UUID productId, ProductContentChange change) {
        requireWritableShop(subject, shopId);
        requireCategory(change.categoryId());
        requireImage(change.imageKey());
        catalog.findProductForUpdate(productId, shopId).orElseThrow(this::productNotFound);
        if (!catalog.updateContent(productId, shopId, change)) {
            throw versionConflict();
        }
        events.record(subject, "ProductUpdated", shopId, "product " + productId + " content");
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    @Transactional
    public SellerProduct changeStatus(
            String subject,
            UUID shopId,
            UUID productId,
            ProductStatus target,
            long expectedVersion) {
        requireWritableShop(subject, shopId);
        Product current =
                catalog.findProductForUpdate(productId, shopId).orElseThrow(this::productNotFound);
        boolean allowed =
                switch (current.status()) {
                    case DRAFT -> target == ProductStatus.ACTIVE;
                    case ACTIVE -> target == ProductStatus.HIDDEN;
                    case HIDDEN -> target == ProductStatus.ACTIVE;
                };
        if (!allowed) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_STATE,
                    "Không thể chuyển sản phẩm từ " + current.status() + " sang " + target + ".");
        }
        if (target == ProductStatus.ACTIVE && catalog.countActiveSkus(productId) == 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_STATE,
                    "Cần ít nhất một SKU đang bán trước khi hiển thị sản phẩm.");
        }
        if (!catalog.updateStatus(productId, shopId, expectedVersion, target)) {
            throw versionConflict();
        }
        events.record(
                subject,
                "ProductStatusChanged",
                shopId,
                "product " + productId + " " + current.status() + " -> " + target);
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    @Transactional
    public SellerProduct addSku(String subject, UUID shopId, UUID productId, NewProduct.Sku sku) {
        requireWritableShop(subject, shopId);
        catalog.findProductForUpdate(productId, shopId).orElseThrow(this::productNotFound);
        SellerProduct current =
                catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
        if (catalog.countSkus(productId) >= MAX_SKUS_PER_PRODUCT) {
            throw invalid("Một sản phẩm tối đa " + MAX_SKUS_PER_PRODUCT + " SKU.");
        }
        List<NewProduct.Option> options =
                current.options().stream()
                        .map(
                                o ->
                                        new NewProduct.Option(
                                                o.option().code(),
                                                o.option().name(),
                                                o.values().stream()
                                                        .map(
                                                                v ->
                                                                        new NewProduct.OptionValue(
                                                                                v.code(),
                                                                                v.label()))
                                                        .toList()))
                        .toList();
        List<String> optionOrder = options.stream().map(NewProduct.Option::code).sorted().toList();
        validateSkus(List.of(sku), options, optionOrder);
        Map<String, UUID> optionIdByCode = new HashMap<>();
        Map<String, Map<String, UUID>> valueIds = new HashMap<>();
        for (SellerProduct.Option option : current.options()) {
            optionIdByCode.put(option.option().code(), option.option().id());
            valueIds.put(
                    option.option().code(),
                    option.values().stream()
                            .collect(Collectors.toMap(OptionValue::code, OptionValue::id)));
        }
        insertSku(shopId, productId, sku, optionOrder, optionIdByCode, valueIds);
        events.record(
                subject, "SkuAdded", shopId, "product " + productId + " sku " + sku.sellerSku());
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    @Transactional
    public SellerProduct updateSku(
            String subject, UUID shopId, UUID productId, UUID skuId, SkuChange change) {
        requireWritableShop(subject, shopId);
        requirePrice(change.price());
        Product product =
                catalog.findProductForUpdate(productId, shopId).orElseThrow(this::productNotFound);
        ProductSku current =
                catalog.findSkuForUpdate(skuId, productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.PRODUCT_NOT_FOUND,
                                                "Không tìm thấy SKU."));
        if (product.status() == ProductStatus.ACTIVE
                && current.status() == SkuStatus.ACTIVE
                && change.status() == SkuStatus.INACTIVE
                && catalog.countActiveSkus(productId) == 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_PRODUCT_STATE,
                    "Không thể ngừng bán SKU cuối cùng của sản phẩm đang hiển thị. Hãy ẩn sản phẩm trước.");
        }
        if (!catalog.updateSku(
                skuId, productId, change.expectedVersion(), change.price(), change.status())) {
            throw versionConflict();
        }
        events.record(
                subject,
                "SkuUpdated",
                shopId,
                "product "
                        + productId
                        + " sku "
                        + current.sellerSku()
                        + " price "
                        + current.price()
                        + " -> "
                        + change.price()
                        + ", "
                        + current.status()
                        + " -> "
                        + change.status());
        return catalog.findProduct(productId, shopId).orElseThrow(this::productNotFound);
    }

    private void insertSku(
            UUID shopId,
            UUID productId,
            NewProduct.Sku sku,
            List<String> optionOrder,
            Map<String, UUID> optionIdByCode,
            Map<String, Map<String, UUID>> valueIds) {
        UUID skuId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean inserted =
                catalog.insertSku(
                        new ProductSku(
                                skuId,
                                productId,
                                shopId,
                                sku.sellerSku(),
                                signature(sku, optionOrder),
                                sku.price(),
                                "VND",
                                SkuStatus.ACTIVE,
                                0,
                                now,
                                now));
        if (!inserted) {
            throw new BusinessException(
                    ErrorCode.DUPLICATE_SKU,
                    "Mã SKU hoặc tổ hợp phân loại này đã tồn tại: " + sku.sellerSku() + ".");
        }
        for (String optionCode : optionOrder) {
            String valueCode = sku.optionValues().get(optionCode);
            catalog.insertSkuOptionValue(
                    new SkuOptionValue(
                            skuId,
                            productId,
                            optionIdByCode.get(optionCode),
                            valueIds.get(optionCode).get(valueCode)));
        }
    }

    /** Returns option codes in canonical (sorted) order after checking uniqueness and limits. */
    private List<String> validateOptions(List<NewProduct.Option> options) {
        if (options.size() > MAX_OPTIONS) {
            throw invalid("Tối đa " + MAX_OPTIONS + " chiều phân loại.");
        }
        Set<String> codes = new HashSet<>();
        for (NewProduct.Option option : options) {
            if (!codes.add(option.code())) {
                throw invalid("Chiều phân loại bị trùng: " + option.code() + ".");
            }
            if (option.values().isEmpty() || option.values().size() > MAX_VALUES_PER_OPTION) {
                throw invalid(
                        "Mỗi chiều phân loại cần từ 1 đến " + MAX_VALUES_PER_OPTION + " giá trị.");
            }
            Set<String> valueCodes = new HashSet<>();
            for (NewProduct.OptionValue value : option.values()) {
                if (!valueCodes.add(value.code())) {
                    throw invalid("Giá trị bị trùng trong " + option.code() + ": " + value.code());
                }
            }
        }
        return codes.stream().sorted().toList();
    }

    private void validateSkus(
            List<NewProduct.Sku> skus, List<NewProduct.Option> options, List<String> optionOrder) {
        if (skus.isEmpty() || skus.size() > MAX_SKUS_PER_PRODUCT) {
            throw invalid("Sản phẩm cần từ 1 đến " + MAX_SKUS_PER_PRODUCT + " SKU.");
        }
        if (options.isEmpty() && skus.size() != 1) {
            throw invalid("Sản phẩm không có phân loại chỉ có đúng một SKU.");
        }
        Map<String, Set<String>> allowed = new HashMap<>();
        options.forEach(
                o ->
                        allowed.put(
                                o.code(),
                                o.values().stream()
                                        .map(NewProduct.OptionValue::code)
                                        .collect(Collectors.toSet())));
        Set<String> sellerCodes = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        for (NewProduct.Sku sku : skus) {
            requirePrice(sku.price());
            if (!sellerCodes.add(sku.sellerSku().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(
                        ErrorCode.DUPLICATE_SKU, "Mã SKU bị trùng: " + sku.sellerSku() + ".");
            }
            if (!sku.optionValues().keySet().equals(allowed.keySet())) {
                throw invalid(
                        "SKU " + sku.sellerSku() + " phải chọn đúng một giá trị mỗi phân loại.");
            }
            sku.optionValues()
                    .forEach(
                            (option, value) -> {
                                if (!allowed.get(option).contains(value)) {
                                    throw invalid(
                                            "SKU "
                                                    + sku.sellerSku()
                                                    + " dùng giá trị không tồn tại: "
                                                    + option
                                                    + "="
                                                    + value);
                                }
                            });
            String signature = signature(sku, optionOrder);
            if (signature.length() > SIGNATURE_LIMIT) {
                throw invalid("Tổ hợp phân loại của SKU " + sku.sellerSku() + " quá dài.");
            }
            if (!signatures.add(signature)) {
                throw new BusinessException(
                        ErrorCode.DUPLICATE_SKU, "Có hai SKU cùng tổ hợp phân loại: " + signature);
            }
        }
    }

    private static String signature(NewProduct.Sku sku, List<String> optionOrder) {
        if (optionOrder.isEmpty()) {
            return "DEFAULT";
        }
        List<String> parts = new ArrayList<>();
        for (String option : optionOrder) {
            parts.add(option + "=" + sku.optionValues().get(option));
        }
        return String.join("|", parts);
    }

    private Shop requireWritableShop(String subject, UUID shopId) {
        accounts.requireActiveAccount(subject);
        Shop shop = shops.ownedShop(shopId, subject);
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_SHOP_STATE,
                    "Gian hàng cần được duyệt trước khi quản lý sản phẩm.");
        }
        return shop;
    }

    private void requireCategory(UUID categoryId) {
        if (!catalog.categoryExists(categoryId)) {
            throw invalid("Danh mục không tồn tại.");
        }
    }

    private void requireImage(String imageKey) {
        if (!IMAGE_KEYS.contains(imageKey)) {
            throw invalid("Ảnh sản phẩm chưa hợp lệ. Hiện chỉ dùng thư viện ảnh có sẵn.");
        }
    }

    private void requirePrice(long price) {
        if (price <= 0 || price > MAX_PRICE_VND) {
            throw invalid("Giá phải từ 1 đến " + MAX_PRICE_VND + " VND.");
        }
    }

    static String slugFor(String name, UUID productId) {
        String base =
                Normalizer.normalize(
                                name.strip().replace('đ', 'd').replace('Đ', 'D'),
                                Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+|-+$", "");
        if (base.length() > 120) {
            base = base.substring(0, 120).replaceAll("-+$", "");
        }
        if (base.isEmpty()) {
            base = "san-pham";
        }
        return base + "-" + productId.toString().substring(0, 8);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_PRODUCT_INPUT, message);
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                ErrorCode.VERSION_CONFLICT,
                "Dữ liệu đã được thay đổi ở nơi khác. Vui lòng tải lại rồi thử lại.");
    }

    private BusinessException productNotFound() {
        return new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm.");
    }
}
