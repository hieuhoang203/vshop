package vn.vshop.modules.inventory.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.exception.ErrorCode;
import vn.vshop.modules.catalog.service.SellerCatalogService;
import vn.vshop.modules.identity.service.AccountService;
import vn.vshop.modules.inventory.entity.MovementType;
import vn.vshop.modules.inventory.entity.StockBalance;
import vn.vshop.modules.inventory.entity.StockMovement;
import vn.vshop.modules.inventory.entity.Warehouse;
import vn.vshop.modules.inventory.entity.WarehouseStatus;
import vn.vshop.modules.inventory.model.StockAdjustment;
import vn.vshop.modules.inventory.repository.InventoryRepository;
import vn.vshop.modules.merchant.entity.Shop;
import vn.vshop.modules.merchant.entity.ShopStatus;
import vn.vshop.modules.merchant.service.ShopService;
import vn.vshop.modules.platform.service.AuditEventService;

/** Seller-facing stock commands: receipts and signed adjustments with idempotency and audit. */
@Service
@Transactional(readOnly = true)
public class StockService {
    static final long MAX_DELTA = 1_000_000L;

    private final InventoryRepository inventory;
    private final SellerCatalogService catalog;
    private final ShopService shops;
    private final AccountService accounts;
    private final AuditEventService events;

    public StockService(
            InventoryRepository inventory,
            SellerCatalogService catalog,
            ShopService shops,
            AccountService accounts,
            AuditEventService events) {
        this.inventory = inventory;
        this.catalog = catalog;
        this.shops = shops;
        this.accounts = accounts;
        this.events = events;
    }

    public List<StockBalance> balances(String subject, UUID shopId, UUID skuId) {
        shops.ownedShop(shopId, subject);
        catalog.shopSku(shopId, skuId);
        return inventory.findBalances(skuId, shopId);
    }

    public List<StockMovement> movements(String subject, UUID shopId, UUID skuId, int limit) {
        shops.ownedShop(shopId, subject);
        catalog.shopSku(shopId, skuId);
        return inventory.recentMovements(skuId, shopId, limit);
    }

    /**
     * Applies the change once per idempotency key. A repeat with the same payload returns the
     * current balance without a second movement; the same key with another payload is rejected.
     */
    @Transactional
    public StockBalance adjust(String subject, UUID shopId, StockAdjustment command) {
        accounts.requireActiveAccount(subject);
        Shop shop = shops.ownedShop(shopId, subject);
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.INVALID_SHOP_STATE,
                    "Gian hàng cần được duyệt trước khi quản lý tồn kho.");
        }
        catalog.shopSku(shopId, command.skuId());
        validate(command);

        Warehouse warehouse = inventory.findOrCreateDefaultWarehouse(shopId);
        if (warehouse.status() != WarehouseStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.INVALID_STOCK_INPUT, "Kho mặc định đang ngừng hoạt động.");
        }
        inventory.ensureBalance(command.skuId(), warehouse.id(), shopId);
        // Serialize all writers of this balance before consulting the idempotency key.
        inventory.lockBalance(command.skuId(), warehouse.id()).orElseThrow();

        String key = "adj:" + shopId + ":" + command.idempotencyKey();
        boolean fresh =
                inventory.insertMovement(
                        new StockMovement(
                                UUID.randomUUID(),
                                command.skuId(),
                                warehouse.id(),
                                command.type(),
                                command.delta(),
                                0,
                                0,
                                key,
                                command.reason(),
                                null));
        if (!fresh) {
            StockMovement previous = inventory.findMovement(key).orElseThrow();
            boolean same =
                    previous.skuId().equals(command.skuId())
                            && previous.movementType() == command.type()
                            && previous.deltaOnHand() == command.delta();
            if (!same) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Khóa idempotency này đã được dùng cho một thao tác khác.");
            }
        } else {
            if (!inventory.applyDelta(command.skuId(), warehouse.id(), command.delta(), 0)) {
                throw new BusinessException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        "Không thể giảm tồn thấp hơn số lượng đang được giữ hoặc khóa.");
            }
            events.record(
                    subject,
                    "StockAdjusted",
                    shopId,
                    "sku "
                            + command.skuId()
                            + " "
                            + command.type()
                            + " "
                            + command.delta()
                            + ": "
                            + abbreviate(command.reason()));
        }
        return inventory.lockBalance(command.skuId(), warehouse.id()).orElseThrow();
    }

    private static void validate(StockAdjustment command) {
        if (command.delta() == 0 || Math.abs(command.delta()) > MAX_DELTA) {
            throw invalid("Số lượng thay đổi phải khác 0 và không quá " + MAX_DELTA + ".");
        }
        if (command.type() == MovementType.RECEIPT && command.delta() < 0) {
            throw invalid("Nhập kho phải có số lượng dương.");
        }
        if (command.type() != MovementType.RECEIPT && command.type() != MovementType.ADJUSTMENT) {
            throw invalid("Loại thao tác tồn kho không được hỗ trợ.");
        }
        if (command.reason() == null || command.reason().strip().length() < 5) {
            throw invalid("Cần nêu lý do ít nhất 5 ký tự.");
        }
    }

    private static String abbreviate(String text) {
        String stripped = text.strip();
        return stripped.length() <= 200 ? stripped : stripped.substring(0, 200);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_STOCK_INPUT, message);
    }
}
