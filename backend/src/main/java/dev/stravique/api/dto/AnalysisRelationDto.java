package dev.stravique.api.dto;

import dev.stravique.domain.AnalysisRelation;

import java.time.Instant;
import java.util.UUID;

public record AnalysisRelationDto(
        UUID id,
        UUID sourceFileId,
        UUID targetFileId,
        String sourceObjectKey,
        String targetObjectKey,
        String relationType,
        String evidencePath,
        Integer lineStart,
        String evidence,
        String ruleId,
        String certainty,
        String analyzerVersion,
        Instant analyzedAt
) {
    public static AnalysisRelationDto from(AnalysisRelation relation) {
        return new AnalysisRelationDto(
                relation.getId(),
                relation.getSourceFileId(),
                relation.getTargetFileId(),
                relation.getSourceObjectKey(),
                relation.getTargetObjectKey(),
                relation.getRelationType().name(),
                relation.getEvidencePath(),
                relation.getLineStart(),
                relation.getEvidence(),
                relation.getRuleId(),
                relation.getCertainty().name(),
                relation.getAnalyzerVersion(),
                relation.getAnalyzedAt()
        );
    }
}
