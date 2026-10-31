package dev.stravique.api.dto;

import java.util.UUID;

public record TransformationBatchItemDto(
        UUID itemId,
        String title,
        UUID runId,
        String status,
        TransformationProposalDto proposal,
        TransformationRunDto run,
        String errorCode,
        String errorMessage
) {
    public static TransformationBatchItemDto success(UUID itemId, String title, String status,
                                                     TransformationProposalDto proposal,
                                                     TransformationRunDto run) {
        return new TransformationBatchItemDto(itemId, title, run == null ? null : run.id(),
                status, proposal, run, null, null);
    }

    public static TransformationBatchItemDto failure(UUID itemId, String title,
                                                     String errorCode, String errorMessage) {
        return new TransformationBatchItemDto(itemId, title, null, "FAILED",
                null, null, errorCode, errorMessage);
    }

    public static TransformationBatchItemDto failureForRun(UUID runId, String errorCode,
                                                           String errorMessage) {
        return new TransformationBatchItemDto(null, null, runId, "FAILED",
                null, null, errorCode, errorMessage);
    }

    public static TransformationBatchItemDto notAttempted(UUID itemId, String title) {
        return new TransformationBatchItemDto(itemId, title, null, "NOT_ATTEMPTED",
                null, null, null, null);
    }
}
