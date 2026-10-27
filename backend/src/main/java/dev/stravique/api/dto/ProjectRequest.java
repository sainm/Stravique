package dev.stravique.api.dto;

import dev.stravique.domain.BuildTool;
import dev.stravique.domain.ProjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 4096) String sourcePath,
        ProjectType projectType,
        @Size(max = 32) String jdkVersion,
        BuildTool buildTool,
        @Size(max = 160) String sourceFramework,
        @Size(max = 160) String targetFramework,
        List<String> excludedDirectories,
        List<String> excludedFiles,
        @Size(max = 40) String analysisScope
) {
}
