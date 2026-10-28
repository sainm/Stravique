package dev.stravique.api.dto;

import java.util.List;

public record MigrationPlanDto(
        MigrationPlanSummaryDto summary,
        List<MigrationPhaseDto> phases
) {
}
