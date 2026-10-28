package dev.stravique.transformation;

import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.ProjectFile;

public interface TransformationRule {

    String ruleId();

    String ruleVersion();

    boolean supports(MigrationItem item, ProjectFile file);

    String propose(String source, String relativePath);
}
