package dev.stravique.api.dto;

import jakarta.validation.constraints.NotNull;

public record TransformationConfirmRequest(
        @NotNull Boolean confirmed
) {
}
