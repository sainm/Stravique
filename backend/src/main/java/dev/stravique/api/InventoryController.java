package dev.stravique.api;

import dev.stravique.api.dto.FileDetailDto;
import dev.stravique.api.dto.FileDto;
import dev.stravique.api.dto.FindingDto;
import dev.stravique.api.dto.FrameworkDetailDto;
import dev.stravique.api.dto.InventoryTreeDto;
import dev.stravique.api.dto.ModuleDto;
import dev.stravique.api.dto.ModuleDependencyDto;
import dev.stravique.api.dto.OverviewDto;
import dev.stravique.api.dto.PageResponse;
import dev.stravique.api.dto.RelationOverviewDto;
import dev.stravique.api.dto.TechnologyDto;
import dev.stravique.application.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryQueryService queryService;

    public InventoryController(InventoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/projects/{projectId}/overview")
    public OverviewDto overview(@PathVariable UUID projectId) {
        return queryService.overview(projectId);
    }

    @GetMapping("/projects/{projectId}/files")
    public PageResponse<FileDto> files(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) String scanStatus,
            @RequestParam(required = false) String structureRole,
            @RequestParam(required = false) UUID moduleId,
            @RequestParam(required = false) String pathPrefix,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "80") int size) {
        return queryService.files(projectId, scanId, q, fileType, scanStatus, structureRole,
                moduleId, pathPrefix, page, size);
    }

    @GetMapping("/projects/{projectId}/tree")
    public InventoryTreeDto tree(@PathVariable UUID projectId,
                                 @RequestParam(required = false) UUID scanId) {
        return queryService.tree(projectId, scanId);
    }

    @GetMapping("/projects/{projectId}/modules")
    public List<ModuleDto> modules(@PathVariable UUID projectId,
                                   @RequestParam(required = false) UUID scanId) {
        return queryService.modules(projectId, scanId);
    }

    @GetMapping("/projects/{projectId}/module-dependencies")
    public List<ModuleDependencyDto> moduleDependencies(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId) {
        return queryService.moduleDependencies(projectId, scanId);
    }

    @GetMapping("/projects/{projectId}/relations")
    public PageResponse<dev.stravique.api.dto.AnalysisRelationDto> relations(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId,
            @RequestParam(required = false) String relationType,
            @RequestParam(required = false) String certainty,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queryService.relations(projectId, scanId, relationType, certainty, q, page, size);
    }

    @GetMapping("/projects/{projectId}/relations/overview")
    public RelationOverviewDto relationOverview(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId) {
        return queryService.relationOverview(projectId, scanId);
    }

    @GetMapping("/files/{fileId}")
    public FileDetailDto file(@PathVariable UUID fileId) {
        return queryService.file(fileId);
    }

    @GetMapping("/projects/{projectId}/findings")
    public PageResponse<FindingDto> findings(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId,
            @RequestParam(required = false) String analysisKind,
            @RequestParam(required = false) String certainty,
            @RequestParam(required = false) String objectKey,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queryService.findings(projectId, scanId, analysisKind, certainty, objectKey, q, page, size);
    }

    @GetMapping("/projects/{projectId}/reviews")
    public PageResponse<FindingDto> reviews(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "80") int size) {
        return queryService.reviews(projectId, scanId, page, size);
    }

    @GetMapping("/projects/{projectId}/frameworks")
    public List<TechnologyDto> frameworks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId) {
        return queryService.technologies(projectId, scanId);
    }

    @GetMapping("/projects/{projectId}/frameworks/{technologyKey}")
    public FrameworkDetailDto framework(
            @PathVariable UUID projectId,
            @PathVariable String technologyKey,
            @RequestParam(required = false) UUID scanId) {
        return queryService.framework(projectId, scanId, technologyKey);
    }
}
