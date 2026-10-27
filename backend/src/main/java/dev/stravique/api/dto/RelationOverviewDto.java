package dev.stravique.api.dto;

import java.util.Map;
import java.util.UUID;

public record RelationOverviewDto(
        UUID scanId,
        long total,
        Map<String, Long> byType,
        Map<String, Long> byCertainty
) {
}
