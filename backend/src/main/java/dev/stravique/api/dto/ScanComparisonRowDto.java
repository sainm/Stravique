package dev.stravique.api.dto;

public record ScanComparisonRowDto(
        String status,
        String relativePath,
        ScanComparisonFileDto before,
        ScanComparisonFileDto after
) {
}
