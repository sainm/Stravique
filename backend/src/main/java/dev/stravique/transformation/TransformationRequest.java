package dev.stravique.transformation;

import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.ProjectFile;

import java.util.List;

public record TransformationRequest(
        MigrationItem item,
        ProjectFile file,
        String source,
        String relativePath,
        List<ControllerRoute> routes,
        List<TransformationCompanion> companions
) {
    public TransformationRequest(MigrationItem item, ProjectFile file, String source,
                                 String relativePath, List<ControllerRoute> routes) {
        this(item, file, source, relativePath, routes, List.of());
    }
}