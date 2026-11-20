package vn.vshop.modules.inventory.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vshop.modules.inventory.repository.InventoryRepository;

/** Read-only stock facts for other modules. Quantities are informational, never a hold. */
@Service
@Transactional(readOnly = true)
public class StockQueryService {
    private final InventoryRepository inventory;

    public StockQueryService(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    /** Sellable quantity per SKU; SKUs without stock are absent from the map. */
    public Map<UUID, Long> available(Collection<UUID> skuIds) {
        return inventory.availableBySku(skuIds);
    }
}
