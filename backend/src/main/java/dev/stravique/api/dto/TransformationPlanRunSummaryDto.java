package dev.stravique.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TransformationPlanRunSummaryDto(
        UUID id,
        UUID planId,
        String status,
        int eligibleCount,
        int appliedCount,
        int failedCount,
        int rolledBackCount,
        int restoredCount,
        boolean cancelRequested,
        Instant createdAt,
        Instant completedAt
) {
}
