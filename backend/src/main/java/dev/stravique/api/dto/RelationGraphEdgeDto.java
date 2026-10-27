package dev.stravique.api.dto;

import java.util.List;

public record RelationGraphEdgeDto(
        String id,
        String source,
        String target,
        List<String> relationTypes,
        List<String> certainties,
        int count
) {
}
