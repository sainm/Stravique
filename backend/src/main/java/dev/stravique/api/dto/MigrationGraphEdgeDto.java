package dev.stravique.api.dto;

import java.util.List;

public record MigrationGraphEdgeDto(
        String id,
        String source,
        String target,
        List<String> relationTypes,
        List<String> certainties,
        int count
) {
}
