package dev.stravique.api.dto;

import dev.stravique.domain.MigrationDependency;

import java.util.UUID;

public record MigrationDependencyDto(
        UUID id,
        UUID fromItemId,
        String fromTitle,
        UUID toItemId,
        String toTitle,
        UUID relationId,
        String relationType,
        String evidencePath,
        Integer lineStart,
        String certainty
) {
    public static MigrationDependencyDto from(MigrationDependency dependency,
                                              String fromTitle, String toTitle) {
        return new MigrationDependencyDto(
                dependency.getId(),
                dependency.getFromItemId(),
                fromTitle,
                dependency.getToItemId(),
                toTitle,
                dependency.getRelationId(),
                dependency.getRelationType().name(),
                dependency.getEvidencePath(),
                dependency.getLineStart(),
                dependency.getCertainty().name()
        );
    }
}
