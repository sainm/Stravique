package dev.stravique.api.dto;

import java.util.List;

public record MigrationPlanComparisonDto(
        MigrationPlanSummaryDto plan,
        MigrationPlanSummaryDto basePlan,
        MigrationPlanComparisonSummaryDto summary,
        List<MigrationPlanComparisonItemDto> items
) {
}
