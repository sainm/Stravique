package dev.stravique.api.dto;

import java.util.UUID;

public record TransformationPlanRunItemDto(
        UUID itemId,
        String title,
        int ordinal,
        String status,
        UUID runId,
        String errorCode,
        String errorMessage
) {
}
