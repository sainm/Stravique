package dev.stravique.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OverviewDto(
        UUID projectId,
        ScanDto currentScan,
        long totalFiles,
        long analyzedFiles,
        long parseFailures,
        long reviewItems,
        long moduleCount,
        Map<String, Long> fileTypes,
        Map<String, Long> structureRoles,
        List<ModuleDto> modules,
        List<TechnologyDto> technologies,
        List<FindingDto> reviewHighlights,
        List<ScanDto> recentScans,
        String nextAction,
        boolean sourceReadOnly
) {
}
