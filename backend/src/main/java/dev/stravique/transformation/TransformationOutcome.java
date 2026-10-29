package dev.stravique.transformation;

import dev.stravique.domain.TransformationArtifactType;

public record TransformationOutcome(
        String ruleId,
        TransformationArtifactType artifactType,
        String targetRelativePath,
        String ruleVersion,
        String proposedText,
        String diff,
        String validationStatus,
        String validationMessage,
        String proposedEncoding
) {
}
