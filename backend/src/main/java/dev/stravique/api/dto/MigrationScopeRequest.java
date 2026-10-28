package dev.stravique.api.dto;

import jakarta.validation.constraints.NotNull;

public record MigrationScopeRequest(
        @NotNull Boolean included
) {
}
