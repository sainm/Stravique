package dev.stravique.api.dto;

import dev.stravique.domain.AnalysisRelation;
import dev.stravique.domain.ProjectFile;

import java.time.Instant;
import java.util.UUID;

public record AnalysisRelationDto(
        UUID id,
        UUID sourceFileId,
        UUID targetFileId,
        String sourceFilePath,
        String targetFilePath,
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
    public static AnalysisRelationDto from(AnalysisRelation relation, ProjectFile source,
                                           ProjectFile target) {
        return new AnalysisRelationDto(
                relation.getId(),
                relation.getSourceFileId(),
                relation.getTargetFileId(),
                source == null ? "" : source.getRelativePath(),
                target == null ? "" : target.getRelativePath(),
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
