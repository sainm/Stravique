package dev.stravique.transformation;

public record RuleDescriptor(
        String ruleId,
        String ruleVersion,
        String itemType,
        String targetKey,
        String language
) {
}