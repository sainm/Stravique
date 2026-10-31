package dev.stravique.api.dto;

import java.util.UUID;

public record TransformationCoverageItemDto(
        UUID itemId,
        String title,
        String itemType,
        String targetKey,
        String status,
        boolean included,
        boolean supported,
        boolean eligible,
        String ineligibleReason,
        String ruleId,
        String ruleVersion,
        int proposalCount,
        int appliedRunCount,
        int restoredRunCount,
        String latestProposalStatus,
        String latestRunStatus
) {
}
