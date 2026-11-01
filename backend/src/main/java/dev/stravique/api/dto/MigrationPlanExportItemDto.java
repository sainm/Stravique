package dev.stravique.api.dto;

import dev.stravique.domain.MigrationItem;

import java.util.UUID;

public record MigrationPlanExportItemDto(
        UUID id,
        String itemKey,
        String itemType,
        String targetKey,
        String title,
        UUID primaryFileId,
        String primaryFilePath,
        UUID primaryFindingId,
        int phase,
        int priority,
        String automationLevel,
        String riskLevel,
        String status,
        boolean included,
        String reasonCode,
        int findingCount,
        int dependencyCount,
        int dependentCount
) {
    public static MigrationPlanExportItemDto from(MigrationItem item, String primaryFilePath) {
        return new MigrationPlanExportItemDto(
                item.getId(),
                item.getItemKey(),
                item.getItemType().name(),
                item.getTargetKey().name(),
                item.getTitle(),
                item.getPrimaryFileId(),
                primaryFilePath,
                item.getPrimaryFindingId(),
                item.getPhase(),
                item.getPriority(),
                item.getAutomationLevel().name(),
                item.getRiskLevel().name(),
                item.getStatus().name(),
                item.isIncluded(),
                item.getReasonCode(),
                item.getFindingCount(),
                item.getDependencyCount(),
                item.getDependentCount()
        );
    }
}
