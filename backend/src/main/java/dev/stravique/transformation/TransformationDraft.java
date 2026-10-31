package dev.stravique.transformation;

import dev.stravique.domain.TransformationArtifactType;

public record TransformationDraft(
        TransformationArtifactType artifactType,
        String targetRelativePath,
        String proposedText,
        String originalText
) {
    public TransformationDraft(TransformationArtifactType artifactType,
                               String targetRelativePath, String proposedText) {
        this(artifactType, targetRelativePath, proposedText, null);
    }
}
