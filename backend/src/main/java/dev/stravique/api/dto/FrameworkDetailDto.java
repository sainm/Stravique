package dev.stravique.api.dto;

import java.util.List;

public record FrameworkDetailDto(
        TechnologyDto technology,
        List<FindingDto> evidence
) {
}
