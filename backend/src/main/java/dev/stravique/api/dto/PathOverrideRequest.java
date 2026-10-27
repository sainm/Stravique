package dev.stravique.api.dto;

import dev.stravique.domain.PathRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PathOverrideRequest(
        @NotBlank @Size(max = 2048) String relativePath,
        @NotNull PathRole role
) {
}
