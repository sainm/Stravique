package dev.stravique.api.dto;

import dev.stravique.domain.AnalysisProject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String name,
        String description,
        String sourcePath,
        String projectType,
        String jdkVersion,
        String buildTool,
        String sourceFramework,
        String targetFramework,
        List<String> excludedDirectories,
        List<String> excludedFiles,
        String analysisScope,
        Instant lastScanAt,
        Instant createdAt,
        Instant updatedAt,
        ScanDto latestScan
) {
    public static ProjectDto from(AnalysisProject project, ScanDto latestScan) {
        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getSourcePath(),
                project.getProjectType().name(),
                project.getJdkVersion(),
                project.getBuildTool().name(),
                project.getSourceFramework(),
                project.getTargetFramework(),
                lines(project.getExcludedDirectories()),
                lines(project.getExcludedFiles()),
                project.getAnalysisScope(),
                project.getLastScanAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                latestScan
        );
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
