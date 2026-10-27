package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.PathOverrideDto;
import dev.stravique.api.dto.PathOverrideRequest;
import dev.stravique.api.dto.ProjectDto;
import dev.stravique.api.dto.ProjectRequest;
import dev.stravique.api.dto.ScanDto;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.BuildTool;
import dev.stravique.domain.PathOverride;
import dev.stravique.domain.ProjectType;
import dev.stravique.domain.ScanRun;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.PathOverrideRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.inventory.ScanPathPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final AnalysisProjectRepository projectRepository;
    private final ScanRunRepository scanRepository;
    private final PathOverrideRepository overrideRepository;
    private final ScanPathPolicy pathPolicy;
    private final AuditService auditService;

    public ProjectService(AnalysisProjectRepository projectRepository, ScanRunRepository scanRepository,
                          PathOverrideRepository overrideRepository, ScanPathPolicy pathPolicy,
                          AuditService auditService) {
        this.projectRepository = projectRepository;
        this.scanRepository = scanRepository;
        this.overrideRepository = overrideRepository;
        this.pathPolicy = pathPolicy;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectDto create(ProjectRequest request) {
        Path canonical = pathPolicy.validateProjectDirectory(request.sourcePath());
        if (projectRepository.existsBySourcePath(canonical.toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_DUPLICATE_SOURCE",
                    "error.project.duplicateSource");
        }
        AnalysisProject project = AnalysisProject.create(request.name().trim(), request.description(),
                canonical.toString());
        apply(request, project);
        project = projectRepository.save(project);
        auditService.record("PROJECT_CREATED", "PROJECT", project.getId().toString(), project.getName());
        return toDto(project);
    }

    @Transactional
    public ProjectDto update(UUID id, ProjectRequest request) {
        AnalysisProject project = requireProject(id);
        Path canonical = pathPolicy.validateProjectDirectory(request.sourcePath());
        if (!canonical.toString().equals(project.getSourcePath())
                && projectRepository.existsBySourcePath(canonical.toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_DUPLICATE_SOURCE",
                    "error.project.duplicateSource");
        }
        project.setName(request.name().trim());
        project.setDescription(request.description() == null ? "" : request.description());
        project.setSourcePath(canonical.toString());
        apply(request, project);
        project.touch();
        project = projectRepository.save(project);
        auditService.record("PROJECT_UPDATED", "PROJECT", project.getId().toString(), project.getName());
        return toDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> list() {
        return projectRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto get(UUID id) {
        return toDto(requireProject(id));
    }

    @Transactional(readOnly = true)
    public AnalysisProject requireProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
    }

    @Transactional(readOnly = true)
    public List<PathOverrideDto> pathOverrides(UUID projectId) {
        requireProject(projectId);
        return overrideRepository.findAllByProjectIdOrderByRelativePathAsc(projectId).stream()
                .map(PathOverrideDto::from).toList();
    }

    @Transactional
    public List<PathOverrideDto> replacePathOverrides(UUID projectId, List<PathOverrideRequest> requests) {
        AnalysisProject project = requireProject(projectId);
        Path root = pathPolicy.resolveExistingProjectDirectory(project.getSourcePath());
        List<PathOverride> overrides = requests.stream().map(request -> {
            Path target = pathPolicy.resolveProjectRelative(root, request.relativePath());
            if (!java.nio.file.Files.isDirectory(target)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "OVERRIDE_PATH_INVALID",
                        "error.override.invalidPath");
            }
            String relative = root.relativize(target).toString().replace('\\', '/');
            return PathOverride.create(projectId, relative.isEmpty() ? "." : relative, request.role());
        }).toList();
        overrideRepository.deleteAllByProjectId(projectId);
        overrideRepository.flush();
        List<PathOverride> saved = overrideRepository.saveAll(overrides);
        auditService.record("PROJECT_PATH_OVERRIDES_UPDATED", "PROJECT", projectId.toString(),
                Integer.toString(saved.size()));
        return saved.stream().map(PathOverrideDto::from).toList();
    }

    private void apply(ProjectRequest request, AnalysisProject project) {
        project.setProjectType(request.projectType() == null ? ProjectType.UNKNOWN : request.projectType());
        project.setJdkVersion(blankToNull(request.jdkVersion()));
        project.setBuildTool(request.buildTool() == null ? BuildTool.UNKNOWN : request.buildTool());
        project.setSourceFramework(blankToNull(request.sourceFramework()));
        project.setTargetFramework(blankToNull(request.targetFramework()));
        project.setExcludedDirectories(joinLines(request.excludedDirectories()));
        project.setExcludedFiles(joinLines(request.excludedFiles()));
        project.setAnalysisScope(request.analysisScope() == null || request.analysisScope().isBlank()
                ? "FULL" : request.analysisScope().trim());
    }

    private ProjectDto toDto(AnalysisProject project) {
        ScanDto latest = scanRepository.findFirstByProjectIdOrderBySequenceDesc(project.getId())
                .map(ScanDto::from).orElse(null);
        return ProjectDto.from(project, latest);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String joinLines(List<String> values) {
        if (values == null) {
            return "";
        }
        return values.stream().map(String::trim).filter(value -> !value.isEmpty())
                .distinct().reduce((left, right) -> left + "\n" + right).orElse("");
    }
}
