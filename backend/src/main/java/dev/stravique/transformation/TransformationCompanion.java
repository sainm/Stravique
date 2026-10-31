package dev.stravique.transformation;

public record TransformationCompanion(
        String relativePath,
        String source,
        String sha256,
        String encoding
) {
}
