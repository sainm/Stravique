package dev.stravique.api;

import dev.stravique.api.dto.PathOverrideDto;
import dev.stravique.api.dto.PathOverrideRequest;
import dev.stravique.api.dto.ProjectDto;
import dev.stravique.api.dto.ProjectRequest;
import dev.stravique.application.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping("/{projectId}")
    public ProjectDto get(@PathVariable UUID projectId) {
        return projectService.get(projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectDto update(@PathVariable UUID projectId, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(projectId, request);
    }

    @GetMapping("/{projectId}/path-overrides")
    public List<PathOverrideDto> getOverrides(@PathVariable UUID projectId) {
        return projectService.pathOverrides(projectId);
    }

    @PutMapping("/{projectId}/path-overrides")
    public List<PathOverrideDto> replaceOverrides(@PathVariable UUID projectId,
                                                  @Valid @RequestBody List<PathOverrideRequest> requests) {
        return projectService.replacePathOverrides(projectId, requests);
    }
}
