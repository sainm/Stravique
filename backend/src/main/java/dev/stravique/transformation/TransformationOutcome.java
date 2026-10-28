package dev.stravique.transformation;

public record TransformationOutcome(
        String ruleId,
        String ruleVersion,
        String proposedText,
        String diff,
        String validationStatus,
        String validationMessage
) {
}
