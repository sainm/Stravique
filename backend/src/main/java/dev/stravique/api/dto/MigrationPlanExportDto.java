package dev.stravique.api.dto;

import java.time.Instant;
import java.util.List;

public record MigrationPlanExportDto(
        MigrationPlanSummaryDto plan,
        List<MigrationPhaseDto> phases,
        List<MigrationPlanExportItemDto> items,
        List<MigrationDependencyDto> dependencies,
        Instant exportedAt
) {
}
