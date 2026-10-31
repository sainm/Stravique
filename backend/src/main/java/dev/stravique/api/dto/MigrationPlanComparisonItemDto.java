package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record MigrationPlanComparisonItemDto(
        String status,
        String itemKey,
        UUID itemId,
        String itemType,
        String targetKey,
        String title,
        String primaryFilePath,
        int phase,
        String automationLevel,
        String riskLevel,
        String itemStatus,
        boolean included,
        List<MigrationPlanComparisonFieldDto> changes
) {
}
