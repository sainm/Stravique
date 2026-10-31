package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record TransformationBackupLedgerDto(
        UUID projectId,
        int total,
        int existing,
        int missing,
        int restorable,
        int prunable,
        long totalBytes,
        List<TransformationBackupItemDto> items
) {
}
