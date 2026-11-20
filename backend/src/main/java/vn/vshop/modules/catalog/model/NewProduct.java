package vn.vshop.modules.catalog.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Command to create a DRAFT product with its variant dimensions and sellable SKUs. */
public record NewProduct(
        String name,
        String description,
        UUID categoryId,
        String imageKey,
        List<Option> options,
        List<Sku> skus) {
    public NewProduct {
        options = List.copyOf(options);
        skus = List.copyOf(skus);
    }

    public record Option(String code, String name, List<OptionValue> values) {
        public Option {
            values = List.copyOf(values);
        }
    }

    public record OptionValue(String code, String label) {}

    /** {@code optionValues} maps option code to the selected value code. */
    public record Sku(String sellerSku, long price, Map<String, String> optionValues) {
        public Sku {
            optionValues = Map.copyOf(optionValues);
        }
    }
}
