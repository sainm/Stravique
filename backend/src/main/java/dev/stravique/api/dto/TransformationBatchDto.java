package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record TransformationBatchDto(
        UUID batchId,
        String status,
        int requested,
        int applied,
        int restored,
        int failed,
        int restoredOnFailure,
        List<TransformationBatchItemDto> items
) {
}
