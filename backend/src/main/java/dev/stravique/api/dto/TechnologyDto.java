package dev.stravique.api.dto;

import dev.stravique.domain.TechnologyUsage;

import java.time.Instant;
import java.util.UUID;

public record TechnologyDto(
        UUID id,
        String technologyKey,
        String name,
        String category,
        String status,
        int apiEvidenceCount,
        int configurationEvidenceCount,
        int dependencyEvidenceCount,
        int totalEvidenceCount,
        String ruleIds,
        String firstEvidencePath,
        Integer firstEvidenceLine,
        String analyzerVersion,
        Instant analyzedAt
) {
    public static TechnologyDto from(TechnologyUsage usage) {
        return new TechnologyDto(
                usage.getId(),
                usage.getTechnologyKey(),
                usage.getName(),
                usage.getCategory().name(),
                usage.getStatus().name(),
                usage.getApiEvidenceCount(),
                usage.getConfigurationEvidenceCount(),
                usage.getDependencyEvidenceCount(),
                usage.getTotalEvidenceCount(),
                usage.getRuleIds(),
                usage.getFirstEvidencePath(),
                usage.getFirstEvidenceLine(),
                usage.getAnalyzerVersion(),
                usage.getAnalyzedAt()
        );
    }
}
