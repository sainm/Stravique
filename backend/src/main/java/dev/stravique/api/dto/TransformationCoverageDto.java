package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record TransformationCoverageDto(
        UUID planId,
        UUID scanId,
        int ruleCount,
        TransformationCoverageSummaryDto summary,
        List<TransformationCoverageItemDto> items
) {
}
