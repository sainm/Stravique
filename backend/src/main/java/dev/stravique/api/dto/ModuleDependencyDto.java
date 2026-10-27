package dev.stravique.api.dto;

import dev.stravique.domain.ModuleDependency;
import dev.stravique.domain.ProjectModule;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ModuleDependencyDto(
        UUID id,
        String fromModulePath,
        String toModulePath,
        String dependencyType,
        String evidencePath,
        Integer lineStart,
        String ruleId,
        String certainty,
        String analyzerVersion,
        Instant analyzedAt
) {
    public static ModuleDependencyDto from(ModuleDependency dependency,
                                           Map<UUID, ProjectModule> modulesById) {
        ProjectModule from = modulesById.get(dependency.getFromModuleId());
        ProjectModule to = modulesById.get(dependency.getToModuleId());
        return new ModuleDependencyDto(
                dependency.getId(),
                from == null ? "" : from.getRelativePath(),
                to == null ? "" : to.getRelativePath(),
                dependency.getDependencyType().name(),
                dependency.getEvidencePath(),
                dependency.getLineStart(),
                dependency.getRuleId(),
                dependency.getCertainty().name(),
                dependency.getAnalyzerVersion(),
                dependency.getAnalyzedAt()
        );
    }
}
