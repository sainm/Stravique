package dev.stravique.api.dto;

public record MigrationPlanComparisonFieldDto(
        String field,
        String before,
        String after
) {
}
