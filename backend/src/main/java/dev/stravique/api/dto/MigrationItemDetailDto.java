package dev.stravique.api.dto;

import java.util.List;

public record MigrationItemDetailDto(
        MigrationItemDto item,
        List<FindingDto> findings,
        List<MigrationDependencyDto> dependencies,
        List<MigrationDependencyDto> dependents
) {
}
