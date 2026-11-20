package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(min = 3, max = 200) String name,
        @NotBlank @Size(min = 10, max = 5000) String description,
        @NotNull UUID categoryId,
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String imageKey,
        @NotNull @Size(max = 3) List<@Valid @NotNull OptionRequest> options,
        @NotEmpty @Size(max = 100) List<@Valid @NotNull SkuRequest> skus) {
    public CreateProductRequest {
        if (name != null) name = name.strip();
        if (description != null) description = description.strip();
    }

    public record OptionRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,49}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotEmpty @Size(max = 30) List<@Valid @NotNull ValueRequest> values) {}

    public record ValueRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,49}") String code,
            @NotBlank @Size(max = 100) String label) {}

    public record SkuRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,79}") String sellerSku,
            @NotNull @Min(1) @Max(1_000_000_000L) Long price,
            @NotNull Map<@NotBlank String, @NotBlank String> optionValues) {}
}
