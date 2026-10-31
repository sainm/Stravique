package dev.stravique.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TransformationPlanRunStartRequest(
        @Size(max = 800) List<UUID> itemIds,
        boolean confirmed
) {
}
