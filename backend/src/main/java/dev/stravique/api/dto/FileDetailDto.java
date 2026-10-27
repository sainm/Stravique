package dev.stravique.api.dto;

import java.util.List;

public record FileDetailDto(FileDto file, List<FindingDto> findings,
                            List<AnalysisRelationDto> relations) {
}
