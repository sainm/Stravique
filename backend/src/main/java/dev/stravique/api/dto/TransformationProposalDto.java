package dev.stravique.api.dto;

import dev.stravique.domain.TransformationProposal;

import java.time.Instant;
import java.util.UUID;

public record TransformationProposalDto(
        UUID id,
        UUID projectId,
        UUID planId,
        UUID itemId,
        UUID sourceFileId,
        String relativePath,
        String ruleId,
        String ruleVersion,
        String artifactType,
        String originalSha256,
        String proposedSha256,
        String diff,
        String validationStatus,
        String validationMessage,
        String status,
        Instant createdAt,
        Instant appliedAt,
        Instant restoredAt
) {
    public static TransformationProposalDto from(TransformationProposal proposal) {
        return new TransformationProposalDto(
                proposal.getId(),
                proposal.getProjectId(),
                proposal.getPlanId(),
                proposal.getItemId(),
                proposal.getSourceFileId(),
                proposal.getRelativePath(),
                proposal.getRuleId(),
                proposal.getRuleVersion(),
                proposal.getArtifactType().name(),
                proposal.getOriginalSha256(),
                proposal.getProposedSha256(),
                proposal.getDiff(),
                proposal.getValidationStatus(),
                proposal.getValidationMessage(),
                proposal.getStatus().name(),
                proposal.getCreatedAt(),
                proposal.getAppliedAt(),
                proposal.getRestoredAt()
        );
    }
}
