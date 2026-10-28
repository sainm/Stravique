package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record RelationGraphDto(
        UUID projectId,
        UUID scanId,
        int relationCount,
        int limit,
        List<RelationGraphNodeDto> nodes,
        List<RelationGraphEdgeDto> edges,
        boolean truncated
) {
}
