package dev.stravique.api.dto;

public record MigrationPhaseDto(
        int phase,
        int itemCount,
        int readyCount,
        int reviewCount,
        int blockedCount
) {
}
