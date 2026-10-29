package dev.stravique.api.dto;

import dev.stravique.domain.TransformationRun;

import java.time.Instant;
import java.util.UUID;

public record TransformationRunDto(
        UUID id,
        UUID projectId,
        UUID planId,
        UUID proposalId,
        UUID itemId,
        String status,
        String relativePath,
        String artifactType,
        String originalSha256,
        String appliedSha256,
        String ruleId,
        String ruleVersion,
        String errorMessage,
        Instant createdAt,
        Instant finishedAt
) {
    public static TransformationRunDto from(TransformationRun run) {
        return new TransformationRunDto(
                run.getId(),
                run.getProjectId(),
                run.getPlanId(),
                run.getProposalId(),
                run.getItemId(),
                run.getStatus().name(),
                run.getRelativePath(),
                run.getArtifactType().name(),
                run.getOriginalSha256(),
                run.getAppliedSha256(),
                run.getRuleId(),
                run.getRuleVersion(),
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getFinishedAt()
        );
    }
}
