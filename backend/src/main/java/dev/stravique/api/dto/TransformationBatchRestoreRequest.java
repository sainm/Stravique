package dev.stravique.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TransformationBatchRestoreRequest(
        @NotEmpty @Size(max = 50) List<UUID> runIds,
        boolean confirmed
) {
}
