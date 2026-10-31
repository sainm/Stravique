package dev.stravique.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TransformationBackupItemDto(
        UUID runId,
        UUID planId,
        UUID itemId,
        String title,
        String relativePath,
        String status,
        String ruleId,
        String ruleVersion,
        boolean exists,
        long sizeBytes,
        boolean restorable,
        boolean prunable,
        Instant createdAt,
        Instant finishedAt
) {
}
