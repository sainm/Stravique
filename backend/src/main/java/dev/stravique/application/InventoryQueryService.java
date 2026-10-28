package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.DirectoryNodeDto;
import dev.stravique.api.dto.AnalysisRelationDto;
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
import dev.stravique.api.dto.RelationGraphDto;
import dev.stravique.api.dto.RelationGraphEdgeDto;
import dev.stravique.api.dto.RelationGraphNodeDto;
import dev.stravique.api.dto.ScanDto;
import dev.stravique.api.dto.TechnologyDto;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.AnalysisRelation;
import dev.stravique.domain.AnalysisRelationType;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileChangeType;
import dev.stravique.domain.FileScanStatus;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.AnalysisFindingRepository;
import dev.stravique.infrastructure.AnalysisRelationRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ProjectModuleRepository;
import dev.stravique.infrastructure.ModuleDependencyRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.infrastructure.TechnologyUsageRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.TreeSet;

@Service
public class InventoryQueryService {

    private static final Set<Certainty> REVIEW_STATES = Set.of(
            Certainty.REVIEW_REQUIRED, Certainty.UNRESOLVED, Certainty.PARSE_FAILED
    );
    private static final Set<ScanStatus> ACTIVE_STATES = Set.of(
            ScanStatus.QUEUED, ScanStatus.RUNNING, ScanStatus.CANCEL_REQUESTED
    );

    private static final int MAX_GRAPH_RELATIONS = 3000;
    private static final Sort GRAPH_ORDER = Sort.by(Sort.Direction.ASC, "relationType")
            .and(Sort.by(Sort.Direction.ASC, "evidencePath"))
            .and(Sort.by(Sort.Direction.ASC, "lineStart"));

    private final AnalysisProjectRepository projectRepository;
    private final ScanRunRepository scanRepository;
    private final ProjectModuleRepository moduleRepository;
    private final ModuleDependencyRepository moduleDependencyRepository;
    private final ProjectFileRepository fileRepository;
    private final AnalysisFindingRepository findingRepository;
    private final AnalysisRelationRepository relationRepository;
    private final TechnologyUsageRepository technologyRepository;

    public InventoryQueryService(AnalysisProjectRepository projectRepository,
                                 ScanRunRepository scanRepository,
                                 ProjectModuleRepository moduleRepository,
                                 ModuleDependencyRepository moduleDependencyRepository,
                                 ProjectFileRepository fileRepository,
                                 AnalysisFindingRepository findingRepository,
                                 AnalysisRelationRepository relationRepository,
                                 TechnologyUsageRepository technologyRepository) {
        this.projectRepository = projectRepository;
        this.scanRepository = scanRepository;
        this.moduleRepository = moduleRepository;
        this.moduleDependencyRepository = moduleDependencyRepository;
        this.fileRepository = fileRepository;
        this.findingRepository = findingRepository;
        this.relationRepository = relationRepository;
        this.technologyRepository = technologyRepository;
    }

    @Transactional(readOnly = true)
    public OverviewDto overview(UUID projectId) {
        requireProject(projectId);
        ScanRun scan = latestScan(projectId).orElse(null);
        List<ScanDto> recentScans = scanRepository.findByProjectIdOrderBySequenceDesc(projectId).stream()
                .limit(6).map(ScanDto::from).toList();
        if (scan == null) {
            return new OverviewDto(projectId, null, 0, 0, 0, 0, 0,
                    0, 0, Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), recentScans,
                    "RUN_SCAN", true);
        }

        List<ProjectFile> files = fileRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        Map<String, Long> fileTypes = countBy(files.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        file -> file.getFileType().name(), TreeMap::new,
                        java.util.stream.Collectors.counting())));
        Map<String, Long> roles = countBy(files.stream()
                .filter(file -> file.getStructureRole() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        ProjectFile::getStructureRole, TreeMap::new,
                        java.util.stream.Collectors.counting())));
        List<Object[]> relationEndpoints = relationRepository.findFileEndpoints(scan.getId());
        long relationCount = relationEndpoints.size();
        long linkedFileCount = relationEndpoints.stream()
                .flatMap(row -> java.util.stream.Stream.of((UUID) row[0], (UUID) row[1]))
                .distinct()
                .count();
        List<dev.stravique.domain.ProjectModule> moduleEntities =
                moduleRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        List<ModuleDto> modules = moduleEntities.stream().map(ModuleDto::from).toList();
        Map<UUID, dev.stravique.domain.ProjectModule> modulesById = moduleEntities.stream()
                .collect(java.util.stream.Collectors.toMap(
                        dev.stravique.domain.ProjectModule::getId, module -> module));
        List<ModuleDependencyDto> moduleDependencies = moduleDependencyRepository
                .findByScanIdOrderByEvidencePathAscLineStartAsc(scan.getId()).stream()
                .map(dependency -> ModuleDependencyDto.from(dependency, modulesById)).toList();
        List<TechnologyDto> technologies = technologyRepository
                .findByScanIdOrderByCategoryAscNameAsc(scan.getId()).stream()
                .map(TechnologyDto::from).toList();
        List<FindingDto> reviews = reviewFindings(scan.getId(), 5);
        String nextAction = nextAction(scan, files, reviews);
        long analyzed = files.stream().filter(file -> file.getScanStatus() == FileScanStatus.ANALYZED).count();
        long parseFailures = files.stream().filter(file -> file.getScanStatus() == FileScanStatus.PARSE_FAILED).count();
        return new OverviewDto(
                projectId,
                ScanDto.from(scan),
                files.size(),
                analyzed,
                parseFailures,
                scan.getReviewCount(),
                modules.size(),
                relationCount,
                linkedFileCount,
                fileTypes,
                roles,
                modules,
                moduleDependencies,
                technologies,
                reviews,
                recentScans,
                nextAction,
                true
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDto> files(UUID projectId, UUID requestedScanId, String query, String fileType,
                                       String scanStatus, String structureRole, UUID moduleId,
                                       String pathPrefix, int page, int size) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return PageResponse.empty(page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size),
                Sort.by(Sort.Direction.ASC, "relativePath"));
        Specification<ProjectFile> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("scanId"), scan.getId()));
            if (query != null && !query.isBlank()) {
                String value = "%" + query.strip().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("relativePath")), value),
                        builder.like(builder.lower(root.get("fileName")), value)
                ));
            }
            if (fileType != null && !fileType.isBlank()) {
                predicates.add(builder.equal(root.get("fileType"), parseEnum(FileType.class, fileType)));
            }
            if (scanStatus != null && !scanStatus.isBlank()) {
                predicates.add(builder.equal(root.get("scanStatus"),
                        parseEnum(FileScanStatus.class, scanStatus)));
            }
            if (structureRole != null && !structureRole.isBlank()) {
                predicates.add(builder.equal(root.get("structureRole"), structureRole));
            }
            if (moduleId != null) {
                predicates.add(builder.equal(root.get("moduleId"), moduleId));
            }
            if (pathPrefix != null && !pathPrefix.isBlank()) {
                String prefix = pathPrefix.replace('\\', '/');
                predicates.add(builder.or(
                        builder.equal(root.get("relativePath"), prefix),
                        builder.like(root.get("relativePath"), prefix + "/%")
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<ProjectFile> result = fileRepository.findAll(specification, pageable);
        return PageResponse.from(result, FileDto::from);
    }

    @Transactional(readOnly = true)
    public FileDetailDto file(UUID fileId) {
        ProjectFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND",
                        "error.file.notFound"));
        List<FindingDto> findings = findingRepository
                .findByScanIdAndFileIdOrderByLineStartAsc(file.getScanId(), fileId).stream()
                .map(FindingDto::from).toList();
        List<AnalysisRelation> relations = relationRepository
                .findForFile(file.getScanId(), fileId).stream()
                .toList();
        List<AnalysisRelationDto> relationDtos = mapRelations(relations);
        return new FileDetailDto(FileDto.from(file), findings, relationDtos);
    }

    @Transactional(readOnly = true)
    public InventoryTreeDto tree(UUID projectId, UUID requestedScanId) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return new InventoryTreeDto(projectId, null, List.of(), List.of(), List.of());
        }
        List<dev.stravique.domain.ProjectModule> moduleEntities =
                moduleRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        List<ModuleDto> modules = moduleEntities.stream().map(ModuleDto::from).toList();
        Map<UUID, dev.stravique.domain.ProjectModule> modulesById = moduleEntities.stream()
                .collect(java.util.stream.Collectors.toMap(
                        dev.stravique.domain.ProjectModule::getId, module -> module));
        List<ModuleDependencyDto> dependencies = moduleDependencyRepository
                .findByScanIdOrderByEvidencePathAscLineStartAsc(scan.getId()).stream()
                .map(dependency -> ModuleDependencyDto.from(dependency, modulesById)).toList();
        List<ProjectFile> files = fileRepository.findByScanIdOrderByRelativePathAsc(scan.getId()).stream()
                .filter(file -> file.getChangeType() != FileChangeType.DELETED)
                .toList();
        return new InventoryTreeDto(projectId, scan.getId(), modules, dependencies, buildTree(files));
    }

    @Transactional(readOnly = true)
    public PageResponse<FindingDto> findings(UUID projectId, UUID requestedScanId, String analysisKind,
                                             String certainty, String objectKey, String query,
                                             int page, int size) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return PageResponse.empty(page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size),
                Sort.by(Sort.Direction.ASC, "filePath").and(Sort.by(Sort.Direction.ASC, "lineStart")));
        Specification<AnalysisFinding> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("scanId"), scan.getId()));
            if (analysisKind != null && !analysisKind.isBlank()) {
                predicates.add(builder.equal(root.get("analysisKind"),
                        parseEnum(AnalysisKind.class, analysisKind)));
            }
            if (certainty != null && !certainty.isBlank()) {
                predicates.add(builder.equal(root.get("certainty"), parseEnum(Certainty.class, certainty)));
            }
            if (objectKey != null && !objectKey.isBlank()) {
                predicates.add(builder.equal(root.get("objectKey"), objectKey));
            }
            if (query != null && !query.isBlank()) {
                String value = "%" + query.strip().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("filePath")), value),
                        builder.like(builder.lower(root.get("summary")), value),
                        builder.like(builder.lower(root.get("ruleId")), value)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(findingRepository.findAll(specification, pageable), FindingDto::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<FindingDto> reviews(UUID projectId, UUID requestedScanId,
                                            int page, int size) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return PageResponse.empty(page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size),
                Sort.by(Sort.Direction.ASC, "filePath").and(Sort.by(Sort.Direction.ASC, "lineStart")));
        Specification<AnalysisFinding> specification = (root, criteriaQuery, builder) ->
                builder.and(
                        builder.equal(root.get("scanId"), scan.getId()),
                        root.get("certainty").in(REVIEW_STATES)
                );
        return PageResponse.from(findingRepository.findAll(specification, pageable), FindingDto::from);
    }

    @Transactional(readOnly = true)
    public List<TechnologyDto> technologies(UUID projectId, UUID requestedScanId) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return List.of();
        }
        return technologyRepository.findByScanIdOrderByCategoryAscNameAsc(scan.getId()).stream()
                .map(TechnologyDto::from).toList();
    }

    @Transactional(readOnly = true)
    public FrameworkDetailDto framework(UUID projectId, UUID requestedScanId, String technologyKey) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FRAMEWORK_NOT_FOUND",
                    "error.framework.notFound");
        }
        var technology = technologyRepository.findByScanIdAndTechnologyKey(scan.getId(), technologyKey)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FRAMEWORK_NOT_FOUND",
                        "error.framework.notFound"));
        return new FrameworkDetailDto(
                TechnologyDto.from(technology),
                findingRepository.findByScanIdAndObjectKeyOrderByFilePathAscLineStartAsc(
                        scan.getId(), technologyKey).stream().map(FindingDto::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<ModuleDto> modules(UUID projectId, UUID requestedScanId) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return List.of();
        }
        return moduleRepository.findByScanIdOrderByRelativePathAsc(scan.getId()).stream()
                .map(ModuleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ModuleDependencyDto> moduleDependencies(UUID projectId, UUID requestedScanId) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return List.of();
        }
        List<dev.stravique.domain.ProjectModule> moduleEntities =
                moduleRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        Map<UUID, dev.stravique.domain.ProjectModule> modulesById = moduleEntities.stream()
                .collect(java.util.stream.Collectors.toMap(
                        dev.stravique.domain.ProjectModule::getId, module -> module));
        return moduleDependencyRepository.findByScanIdOrderByEvidencePathAscLineStartAsc(scan.getId())
                .stream().map(dependency -> ModuleDependencyDto.from(dependency, modulesById)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AnalysisRelationDto> relations(
            UUID projectId,
            UUID requestedScanId,
            String relationType,
            String certainty,
            String query,
            int page,
            int size) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return PageResponse.empty(page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size),
                Sort.by(Sort.Direction.ASC, "relationType")
                        .and(Sort.by(Sort.Direction.ASC, "evidencePath"))
                        .and(Sort.by(Sort.Direction.ASC, "lineStart")));
        Specification<AnalysisRelation> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("scanId"), scan.getId()));
            if (relationType != null && !relationType.isBlank()) {
                predicates.add(builder.equal(root.get("relationType"),
                        parseEnum(AnalysisRelationType.class, relationType)));
            }
            if (certainty != null && !certainty.isBlank()) {
                predicates.add(builder.equal(root.get("certainty"),
                        parseEnum(Certainty.class, certainty)));
            }
            if (query != null && !query.isBlank()) {
                String value = "%" + query.strip().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sourceObjectKey")), value),
                        builder.like(builder.lower(root.get("targetObjectKey")), value),
                        builder.like(builder.lower(root.get("evidencePath")), value)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<AnalysisRelation> result = relationRepository.findAll(specification, pageable);
        List<AnalysisRelationDto> content = mapRelations(result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public RelationOverviewDto relationOverview(UUID projectId, UUID requestedScanId) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return new RelationOverviewDto(null, 0, Map.of(), Map.of());
        }
        Map<String, Long> byType = countRelations(relationRepository.countByType(scan.getId()));
        Map<String, Long> byCertainty =
                countRelations(relationRepository.countByCertainty(scan.getId()));
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        return new RelationOverviewDto(scan.getId(), total, byType, byCertainty);
    }

    @Transactional(readOnly = true)
    public RelationGraphDto relationGraph(UUID projectId, UUID requestedScanId,
                                          String relationType, String certainty,
                                          String query) {
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return new RelationGraphDto(projectId, null, 0, List.of(), List.of(), false);
        }
        Specification<AnalysisRelation> specification = relationSpecification(
                scan.getId(), relationType, certainty, query);
        long total = relationRepository.count(specification);
        List<AnalysisRelation> relations = relationRepository.findAll(specification,
                        PageRequest.of(0, MAX_GRAPH_RELATIONS, GRAPH_ORDER))
                .getContent();
        Map<UUID, ProjectFile> filesById = relationFiles(relations);
        Map<UUID, int[]> counts = new LinkedHashMap<>();
        Map<String, EdgeAccumulator> edges = new LinkedHashMap<>();
        for (AnalysisRelation relation : relations) {
            counts.computeIfAbsent(relation.getSourceFileId(), ignored -> new int[2])[1]++;
            counts.computeIfAbsent(relation.getTargetFileId(), ignored -> new int[2])[0]++;
            String key = relation.getSourceFileId() + "->" + relation.getTargetFileId();
            edges.computeIfAbsent(key, ignored -> new EdgeAccumulator(
                    relation.getSourceFileId(), relation.getTargetFileId()))
                    .add(relation);
        }
        List<RelationGraphNodeDto> nodes = counts.entrySet().stream()
                .map(entry -> {
                    ProjectFile file = filesById.get(entry.getKey());
                    return new RelationGraphNodeDto(
                            entry.getKey().toString(),
                            file == null ? "" : file.getRelativePath(),
                            file == null ? entry.getKey().toString() : file.getFileName(),
                            file == null ? "OTHER" : file.getFileType().name(),
                            entry.getValue()[0],
                            entry.getValue()[1]
                    );
                })
                .sorted(Comparator.comparing(RelationGraphNodeDto::path))
                .toList();
        List<RelationGraphEdgeDto> graphEdges = edges.values().stream()
                .map(EdgeAccumulator::toDto)
                .sorted(Comparator.comparing(RelationGraphEdgeDto::id))
                .toList();
        return new RelationGraphDto(projectId, scan.getId(), (int) Math.min(total, Integer.MAX_VALUE),
                nodes, graphEdges, total > relations.size());
    }

    private List<FindingDto> reviewFindings(UUID scanId, int limit) {
        Page<AnalysisFinding> page = findingRepository.findAll((root, query, builder) ->
                        builder.and(
                                builder.equal(root.get("scanId"), scanId),
                                root.get("certainty").in(REVIEW_STATES)
                        ),
                PageRequest.of(0, limit,
                        Sort.by(Sort.Direction.ASC, "filePath").and(Sort.by(Sort.Direction.ASC, "lineStart"))));
        return page.getContent().stream().map(FindingDto::from).toList();
    }

    private String nextAction(ScanRun scan, List<ProjectFile> files, List<FindingDto> reviews) {
        if (ACTIVE_STATES.contains(scan.getStatus())) {
            return "WAIT_FOR_SCAN";
        }
        if (scan.getStatus() == ScanStatus.FAILED) {
            return "RETRY_SCAN";
        }
        if (files.stream().anyMatch(file -> file.getScanStatus() == FileScanStatus.PARSE_FAILED)) {
            return "REVIEW_PARSE_FAILURES";
        }
        if (!reviews.isEmpty()) {
            return "REVIEW_UNCERTAINTIES";
        }
        return "INSPECT_RESULTS";
    }

    private ScanRun resolveScan(UUID projectId, UUID requestedScanId) {
        requireProject(projectId);
        if (requestedScanId == null) {
            return latestScan(projectId).orElse(null);
        }
        ScanRun scan = scanRepository.findById(requestedScanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                        "error.scan.notFound"));
        if (!scan.getProjectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "error.scan.notFound");
        }
        return scan;
    }

    private java.util.Optional<ScanRun> latestScan(UUID projectId) {
        return scanRepository.findFirstByProjectIdOrderBySequenceDesc(projectId);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                    "error.project.notFound");
        }
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER",
                    "error.validation", "filter");
        }
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(200, size));
    }

    private Map<String, Long> countBy(Map<String, Long> values) {
        return new LinkedHashMap<>(values);
    }

    private List<DirectoryNodeDto> buildTree(List<ProjectFile> files) {
        MutableDirectory root = new MutableDirectory("", "");
        for (ProjectFile file : files) {
            String[] segments = file.getRelativePath().split("/");
            MutableDirectory current = root;
            current.fileCount++;
            StringBuilder path = new StringBuilder();
            for (int index = 0; index < segments.length - 1; index++) {
                final int segmentIndex = index;
                if (index > 0) {
                    path.append('/');
                }
                path.append(segments[segmentIndex]);
                final String currentPath = path.toString();
                current = current.children.computeIfAbsent(segments[segmentIndex],
                        ignored -> new MutableDirectory(segments[segmentIndex], currentPath));
                current.fileCount++;
            }
        }
        return root.toDtos();
    }

    private List<AnalysisRelationDto> mapRelations(List<AnalysisRelation> relations) {
        Set<UUID> fileIds = new java.util.LinkedHashSet<>();
        for (AnalysisRelation relation : relations) {
            fileIds.add(relation.getSourceFileId());
            fileIds.add(relation.getTargetFileId());
        }
        Map<UUID, ProjectFile> filesById = fileRepository.findAllById(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(ProjectFile::getId, file -> file));
        return relations.stream().map(relation -> AnalysisRelationDto.from(
                relation,
                filesById.get(relation.getSourceFileId()),
                filesById.get(relation.getTargetFileId())
        )).toList();
    }

    private Map<String, Long> countRelations(List<Object[]> rows) {
        Map<String, Long> result = new TreeMap<>();
        for (Object[] row : rows) {
            result.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return result;
    }

    private Specification<AnalysisRelation> relationSpecification(
            UUID scanId, String relationType, String certainty, String query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("scanId"), scanId));
            if (relationType != null && !relationType.isBlank()) {
                predicates.add(builder.equal(root.get("relationType"),
                        parseEnum(AnalysisRelationType.class, relationType)));
            }
            if (certainty != null && !certainty.isBlank()) {
                predicates.add(builder.equal(root.get("certainty"),
                        parseEnum(Certainty.class, certainty)));
            }
            if (query != null && !query.isBlank()) {
                String value = "%" + query.strip().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("sourceObjectKey")), value),
                        builder.like(builder.lower(root.get("targetObjectKey")), value),
                        builder.like(builder.lower(root.get("evidencePath")), value)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<UUID, ProjectFile> relationFiles(List<AnalysisRelation> relations) {
        Set<UUID> fileIds = new java.util.LinkedHashSet<>();
        for (AnalysisRelation relation : relations) {
            fileIds.add(relation.getSourceFileId());
            fileIds.add(relation.getTargetFileId());
        }
        return fileRepository.findAllById(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(ProjectFile::getId, file -> file));
    }

    private static final class MutableDirectory {
        private final String name;
        private final String path;
        private long fileCount;
        private final Map<String, MutableDirectory> children = new TreeMap<>();

        private MutableDirectory(String name, String path) {
            this.name = name;
            this.path = path;
        }

        private List<DirectoryNodeDto> toDtos() {
            return children.values().stream()
                    .map(child -> new DirectoryNodeDto(child.name, child.path, child.fileCount, child.toDtos()))
                    .toList();
        }
    }

    private static final class EdgeAccumulator {
        private final UUID source;
        private final UUID target;
        private int count;
        private final Set<String> relationTypes = new TreeSet<>();
        private final Set<String> certainties = new TreeSet<>();

        private EdgeAccumulator(UUID source, UUID target) {
            this.source = source;
            this.target = target;
        }

        private void add(AnalysisRelation relation) {
            count++;
            relationTypes.add(relation.getRelationType().name());
            certainties.add(relation.getCertainty().name());
        }

        private RelationGraphEdgeDto toDto() {
            String id = source + "->" + target;
            return new RelationGraphEdgeDto(id, source.toString(), target.toString(),
                    List.copyOf(relationTypes), List.copyOf(certainties), count);
        }
    }
}
