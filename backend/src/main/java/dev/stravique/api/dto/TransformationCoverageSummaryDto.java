package dev.stravique.api.dto;

public record TransformationCoverageSummaryDto(
        int items,
        int supported,
        int unsupported,
        int eligible,
        int excluded,
        int reviewRequired,
        int blocked,
        int proposals,
        int appliedRuns,
        int restoredRuns
) {
}
