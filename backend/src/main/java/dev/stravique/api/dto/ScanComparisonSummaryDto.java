package dev.stravique.api.dto;

public record ScanComparisonSummaryDto(
        int added,
        int removed,
        int changed,
        int unchanged,
        int unknown
) {
}
