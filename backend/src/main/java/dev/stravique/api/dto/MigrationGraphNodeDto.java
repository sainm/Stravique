package dev.stravique.api.dto;

public record MigrationGraphNodeDto(
        String id,
        String title,
        String filePath,
        String itemType,
        String targetKey,
        String status,
        String riskLevel,
        int phase,
        int priority
) {
}
