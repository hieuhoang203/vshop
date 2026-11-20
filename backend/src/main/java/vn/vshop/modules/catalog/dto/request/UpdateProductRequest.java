package vn.vshop.modules.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateProductRequest(
        @NotBlank @Size(min = 3, max = 200) String name,
        @NotBlank @Size(min = 10, max = 5000) String description,
        @NotNull UUID categoryId,
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String imageKey,
        @NotNull @Min(0) Long expectedVersion) {
    public UpdateProductRequest {
        if (name != null) name = name.strip();
        if (description != null) description = description.strip();
    }
}
