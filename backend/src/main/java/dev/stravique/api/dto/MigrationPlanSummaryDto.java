package dev.stravique.api.dto;

import dev.stravique.domain.MigrationPlan;

import java.time.Instant;
import java.util.UUID;

public record MigrationPlanSummaryDto(
        UUID id,
        UUID projectId,
        UUID scanId,
        int sequence,
        String sourceProfile,
        String targetProfile,
        int sourceScanSequence,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        int includedCount,
        int readyCount,
        int reviewCount,
        int blockedCount,
        int dependencyCount,
        int phaseCount,
        String analyzerVersion,
        boolean stale
) {
    public static MigrationPlanSummaryDto from(MigrationPlan plan, boolean stale) {
        return new MigrationPlanSummaryDto(
                plan.getId(),
                plan.getProjectId(),
                plan.getScanId(),
                plan.getSequence(),
                plan.getSourceProfile(),
                plan.getTargetProfile().name(),
                plan.getSourceScanSequence(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                plan.getItemCount(),
                plan.getIncludedCount(),
                plan.getReadyCount(),
                plan.getReviewCount(),
                plan.getBlockedCount(),
                plan.getDependencyCount(),
                plan.getPhaseCount(),
                plan.getAnalyzerVersion(),
                stale
        );
    }
}
