package dev.stravique.api.dto;

import dev.stravique.domain.MigrationItem;

import java.util.UUID;

public record MigrationItemDto(
        UUID id,
        UUID planId,
        String itemKey,
        String itemType,
        String targetKey,
        String title,
        UUID primaryFileId,
        String primaryFilePath,
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
    public static MigrationItemDto from(MigrationItem item, String primaryFilePath) {
        return new MigrationItemDto(
                item.getId(),
                item.getPlanId(),
                item.getItemKey(),
                item.getItemType().name(),
                item.getTargetKey().name(),
                item.getTitle(),
                item.getPrimaryFileId(),
                primaryFilePath,
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
