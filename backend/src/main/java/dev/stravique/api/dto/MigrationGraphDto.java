package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record MigrationGraphDto(
        UUID planId,
        int itemCount,
        int edgeCount,
        int itemLimit,
        int edgeLimit,
        boolean truncated,
        List<MigrationGraphNodeDto> nodes,
        List<MigrationGraphEdgeDto> edges
) {
}
