package dev.stravique.transformation;

import com.github.javaparser.StaticJavaParser;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;

public interface TransformationRule {

    String ruleId();

    String ruleVersion();

    MigrationItemType itemType();

    MigrationTarget target();

    default String language() {
        return "JAVA";
    }

    default String proposedEncoding() {
        return null;
    }

    boolean supports(MigrationItem item, ProjectFile file);

    default void validate(String proposed) {
        StaticJavaParser.parse(proposed);
    }

    default TransformationDraft propose(TransformationRequest request) {
        return new TransformationDraft(dev.stravique.domain.TransformationArtifactType.EDIT_FILE,
                request.relativePath(), propose(request.source(), request.relativePath()));
    }

    default String propose(String source, String relativePath) {
        throw new TransformationUnsupportedException(
                "This rule generates a new artifact and needs its request context.");
    }
}
