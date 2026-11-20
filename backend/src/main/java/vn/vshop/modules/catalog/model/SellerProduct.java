package vn.vshop.modules.catalog.model;

import java.util.List;
import java.util.Map;
import vn.vshop.modules.catalog.entity.OptionValue;
import vn.vshop.modules.catalog.entity.Product;
import vn.vshop.modules.catalog.entity.ProductOption;
import vn.vshop.modules.catalog.entity.ProductSku;

/** Seller-side read model: product content, variant dimensions and SKUs with sellable stock. */
public record SellerProduct(Product product, List<Option> options, List<Sku> skus) {
    public SellerProduct {
        options = List.copyOf(options);
        skus = List.copyOf(skus);
    }

    public record Option(ProductOption option, List<OptionValue> values) {
        public Option {
            values = List.copyOf(values);
        }
    }

    /** {@code available} sums on_hand - reserved - blocked over active warehouses. */
    public record Sku(ProductSku sku, Map<String, String> optionValues, long available) {
        public Sku {
            optionValues = Map.copyOf(optionValues);
        }
    }
}
