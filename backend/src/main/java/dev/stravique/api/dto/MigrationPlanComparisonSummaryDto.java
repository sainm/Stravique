package dev.stravique.api.dto;

public record MigrationPlanComparisonSummaryDto(
        int added,
        int removed,
        int changed,
        int unchanged
) {
}
