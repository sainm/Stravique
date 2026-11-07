package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record ScanComparisonDto(
        UUID projectId,
        ScanDto scan,
        ScanDto baseScan,
        ScanComparisonSummaryDto summary,
        List<ScanComparisonRowDto> rows,
        boolean truncated,
        int rowLimit
) {
}
