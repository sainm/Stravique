package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.FindingDto;
import dev.stravique.api.dto.MigrationDependencyDto;
import dev.stravique.api.dto.MigrationGraphDto;
import dev.stravique.api.dto.MigrationGraphEdgeDto;
import dev.stravique.api.dto.MigrationGraphNodeDto;
import dev.stravique.api.dto.MigrationItemDetailDto;
import dev.stravique.api.dto.MigrationItemDto;
import dev.stravique.api.dto.MigrationPhaseDto;
import dev.stravique.api.dto.MigrationPlanDto;
import dev.stravique.api.dto.MigrationPlanRequest;
import dev.stravique.api.dto.MigrationPlanSummaryDto;
import dev.stravique.api.dto.PageResponse;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.MigrationDependency;
import dev.stravique.domain.MigrationEvidenceRole;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemFinding;
import dev.stravique.domain.MigrationItemStatus;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationPlan;
import dev.stravique.domain.MigrationTargetProfile;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.AnalysisFindingRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.AnalysisRelationRepository;
import dev.stravique.infrastructure.MigrationDependencyRepository;
import dev.stravique.infrastructure.MigrationItemFindingRepository;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.MigrationPlanRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MigrationPlanService {

    private static final int MAX_GRAPH_ITEMS = 800;
    private static final int MAX_GRAPH_EDGES = 3000;
    static final Sort ITEM_ORDER = Sort.by(Sort.Direction.ASC, "phase")
            .and(Sort.by(Sort.Direction.ASC, "priority"))
            .and(Sort.by(Sort.Direction.ASC, "title"))
            .and(Sort.by(Sort.Direction.ASC, "id"));
    private static final List<MigrationItemStatus> ATTENTION_STATUSES = List.of(
            MigrationItemStatus.REVIEW_REQUIRED, MigrationItemStatus.BLOCKED
    );

    private final AnalysisProjectRepository projectRepository;
    private final ScanRunRepository scanRepository;
    private final ProjectFileRepository fileRepository;
    private final AnalysisFindingRepository findingRepository;
    private final AnalysisRelationRepository relationRepository;
    private final TechnologyUsageRepository technologyRepository;
    private final MigrationPlanRepository planRepository;
    private final MigrationItemRepository itemRepository;
    private final MigrationDependencyRepository dependencyRepository;
    private final MigrationItemFindingRepository itemFindingRepository;
    private final MigrationPlanner planner;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public MigrationPlanService(AnalysisProjectRepository projectRepository,
                                ScanRunRepository scanRepository,
                                ProjectFileRepository fileRepository,
                                AnalysisFindingRepository findingRepository,
                                AnalysisRelationRepository relationRepository,
                                TechnologyUsageRepository technologyRepository,
                                MigrationPlanRepository planRepository,
                                MigrationItemRepository itemRepository,
                                MigrationDependencyRepository dependencyRepository,
                                MigrationItemFindingRepository itemFindingRepository,
                                MigrationPlanner planner,
                                AuditService auditService,
                                PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
        this.findingRepository = findingRepository;
        this.relationRepository = relationRepository;
        this.technologyRepository = technologyRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.dependencyRepository = dependencyRepository;
        this.itemFindingRepository = itemFindingRepository;
        this.planner = planner;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<MigrationPlanSummaryDto> plans(UUID projectId) {
        requireProject(projectId);
        UUID latestScanId = latestCompletedScan(projectId).map(ScanRun::getId).orElse(null);
        return planRepository.findTop20ByProjectIdOrderBySequenceDesc(projectId).stream()
                .map(plan -> MigrationPlanSummaryDto.from(plan, isStale(plan, latestScanId)))
                .toList();
    }

    public MigrationPlanDto generate(UUID projectId, MigrationPlanRequest request) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return transactionTemplate.execute(status -> doGenerate(projectId, request));
        } finally {
            lock.unlock();
        }
    }

    private MigrationPlanDto doGenerate(UUID projectId, MigrationPlanRequest request) {
        AnalysisProject project = requireProjectEntity(projectId);
        ScanRun scan = resolveScanForPlan(project, request == null ? null : request.scanId());
        MigrationTargetProfile targetProfile = targetProfile(request == null ? null : request.targetProfile());
        var files = fileRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        var findings = findingRepository.findByScanIdOrderByFilePathAscLineStartAsc(scan.getId());
        var relations = relationRepository.findByScanIdOrderByEvidencePathAscLineStartAsc(scan.getId());
        var technologies = technologyRepository.findByScanIdOrderByCategoryAscNameAsc(scan.getId());
        MigrationPlanner.Draft draft = planner.plan(files, findings, relations, technologies);
        int sequence = planRepository.findFirstByProjectIdOrderBySequenceDesc(projectId)
                .map(plan -> plan.getSequence() + 1).orElse(1);
        MigrationPlan plan = planRepository.save(MigrationPlan.create(projectId, scan.getId(), sequence,
                draft.sourceProfile(), targetProfile, scan.getSequence(), scan.getAnalyzerVersion()));
        Map<String, MigrationItem> items = new LinkedHashMap<>();
        for (MigrationPlanner.ItemDraft itemDraft : draft.items()) {
            MigrationItem item = MigrationItem.create(plan.getId(), projectId, scan.getId(),
                    itemDraft.key(), itemDraft.type(), itemDraft.target(), itemDraft.title(),
                    itemDraft.primaryFileId(), itemDraft.primaryFindingId(), itemDraft.priority(),
                    itemDraft.automation(), itemDraft.risk(), itemDraft.status(),
                    itemDraft.reasonCode());
            items.put(itemDraft.key(), item);
        }
        List<MigrationItem> itemList = new ArrayList<>(items.values());
        itemRepository.saveAll(itemList);
        List<MigrationItemFinding> links = new ArrayList<>();
        for (MigrationPlanner.ItemDraft itemDraft : draft.items()) {
            MigrationItem item = items.get(itemDraft.key());
            for (UUID findingId : new TreeSet<>(itemDraft.findingIds())) {
                MigrationEvidenceRole role = findingId.equals(itemDraft.primaryFindingId())
                        ? MigrationEvidenceRole.PRIMARY : MigrationEvidenceRole.SUPPORTING;
                links.add(MigrationItemFinding.create(item.getId(), findingId, role));
            }
        }
        itemFindingRepository.saveAll(links);
        List<MigrationDependency> dependencies = new ArrayList<>();
        Set<String> dependencyKeys = new HashSet<>();
        for (MigrationPlanner.DependencyDraft dependencyDraft : draft.dependencies()) {
            MigrationItem from = items.get(dependencyDraft.fromKey());
            MigrationItem to = items.get(dependencyDraft.toKey());
            if (from == null || to == null) {
                continue;
            }
            String key = from.getId() + "|" + to.getId() + "|" + dependencyDraft.relationType()
                    + "|" + dependencyDraft.evidencePath() + "|" + dependencyDraft.lineStart();
            if (!dependencyKeys.add(key)) {
                continue;
            }
            dependencies.add(MigrationDependency.create(plan.getId(), projectId, scan.getId(),
                    from.getId(), to.getId(), dependencyDraft.relationId(),
                    dependencyDraft.relationType(), dependencyDraft.evidencePath(),
                    dependencyDraft.lineStart(), dependencyDraft.certainty()));
        }
        dependencyRepository.saveAll(dependencies);
        assignPhases(itemList, dependencies);
        updateItemCounts(itemList, links, dependencies);
        itemRepository.saveAll(itemList);
        refreshPlanCounts(plan, itemList, dependencies);
        planRepository.save(plan);
        auditService.record(projectId, "MIGRATION_PLAN_GENERATED", "MIGRATION_PLAN",
                plan.getId().toString(), Integer.toString(sequence));
        return planDto(plan);
    }

    @Transactional(readOnly = true)
    public MigrationPlanDto plan(UUID projectId, UUID planId) {
        return planDto(requirePlan(projectId, planId));
    }

    @Transactional(readOnly = true)
    public PageResponse<MigrationItemDto> items(UUID projectId, UUID planId, String status,
                                                String itemType, Integer phase, Boolean attention,
                                                Boolean included, String query, int page, int size) {
        MigrationPlan plan = requirePlan(projectId, planId);
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size), ITEM_ORDER);
        MigrationItemStatus statusFilter = status == null || status.isBlank()
                ? null : parseStatus(status);
        MigrationItemType typeFilter = itemType == null || itemType.isBlank()
                ? null : parseType(itemType);
        Specification<MigrationItem> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("planId"), plan.getId()));
            if (statusFilter != null) {
                predicates.add(builder.equal(root.get("status"), statusFilter));
            }
            if (Boolean.TRUE.equals(attention)) {
                predicates.add(root.get("status").in(ATTENTION_STATUSES));
            }
            if (typeFilter != null) {
                predicates.add(builder.equal(root.get("itemType"), typeFilter));
            }
            if (phase != null) {
                predicates.add(builder.equal(root.get("phase"), phase));
            }
            if (included != null) {
                predicates.add(builder.equal(root.get("included"), included));
            }
            if (query != null && !query.isBlank()) {
                String value = "%" + query.strip().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), value),
                        builder.like(builder.lower(root.get("itemKey")), value),
                        builder.like(builder.lower(root.get("reasonCode")), value)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<MigrationItem> result = itemRepository.findAll(specification, pageable);
        Map<UUID, String> paths = filePaths(result.getContent());
        return new PageResponse<>(result.getContent().stream()
                .map(item -> MigrationItemDto.from(item, pathFor(item, paths)))
                .toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MigrationItemDetailDto item(UUID projectId, UUID planId, UUID itemId) {
        MigrationPlan plan = requirePlan(projectId, planId);
        MigrationItem item = requireItem(plan.getId(), itemId);
        return itemDetail(item);
    }

    public MigrationItemDto setIncluded(UUID projectId, UUID planId, UUID itemId, boolean included) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return transactionTemplate.execute(status -> {
                MigrationPlan plan = requirePlan(projectId, planId);
                MigrationItem item = requireItem(plan.getId(), itemId);
                List<MigrationItem> allItems = itemRepository.findByPlanIdOrderByPhaseAscPriorityAscTitleAsc(
                        plan.getId());
                List<MigrationDependency> allDependencies =
                        dependencyRepository.findByPlanIdOrderByEvidencePathAscLineStartAsc(plan.getId());
                Map<UUID, MigrationItem> byId = new HashMap<>();
                allItems.forEach(current -> byId.put(current.getId(), current));
                if (!included) {
                    long dependents = allDependencies.stream()
                            .filter(dependency -> dependency.getToItemId().equals(itemId))
                            .map(MigrationDependency::getFromItemId)
                            .map(byId::get)
                            .filter(current -> current != null && current.isIncluded())
                            .count();
                    if (dependents > 0) {
                        throw new ApiException(HttpStatus.CONFLICT, "MIGRATION_DEPENDENCY_CONFLICT",
                                "error.migration.dependencyConflict", dependents);
                    }
                } else {
                    long excludedPrerequisites = allDependencies.stream()
                            .filter(dependency -> dependency.getFromItemId().equals(itemId))
                            .map(MigrationDependency::getToItemId)
                            .map(byId::get)
                            .filter(prerequisite -> prerequisite != null && !prerequisite.isIncluded())
                            .count();
                    if (excludedPrerequisites > 0) {
                        throw new ApiException(HttpStatus.CONFLICT,
                                "MIGRATION_PREREQUISITE_EXCLUDED",
                                "error.migration.prerequisiteExcluded", excludedPrerequisites);
                    }
                }
                if (item.isIncluded() != included) {
                    item.setIncluded(included);
                    itemRepository.save(item);
                    refreshPlanCounts(plan, allItems, allDependencies);
                    planRepository.save(plan);
                    auditService.record(projectId, "MIGRATION_SCOPE_UPDATED", "MIGRATION_ITEM",
                            item.getId().toString(), included ? "included" : "excluded");
                }
                Map<UUID, String> paths = filePaths(List.of(item));
                return MigrationItemDto.from(item, pathFor(item, paths));
            });
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public MigrationGraphDto graph(UUID projectId, UUID planId) {
        MigrationPlan plan = requirePlan(projectId, planId);
        List<MigrationItem> included = itemRepository
                .findByPlanIdAndIncludedTrueOrderByPhaseAscPriorityAscTitleAscIdAsc(plan.getId());
        List<MigrationDependency> dependencies =
                dependencyRepository.findByPlanIdOrderByEvidencePathAscLineStartAsc(plan.getId());
        Set<UUID> includedIds = new HashSet<>();
        included.forEach(item -> includedIds.add(item.getId()));
        long trueEdgeCount = dependencies.stream()
                .filter(dependency -> includedIds.contains(dependency.getFromItemId())
                        && includedIds.contains(dependency.getToItemId()))
                .count();
        List<MigrationItem> selected = included.stream().limit(MAX_GRAPH_ITEMS).toList();
        Set<UUID> selectedIds = new HashSet<>();
        selected.forEach(item -> selectedIds.add(item.getId()));
        List<MigrationDependency> selectedDependencies = dependencies.stream()
                .filter(dependency -> selectedIds.contains(dependency.getFromItemId())
                        && selectedIds.contains(dependency.getToItemId()))
                .limit(MAX_GRAPH_EDGES)
                .toList();
        Map<UUID, String> paths = filePaths(selected);
        List<MigrationGraphNodeDto> nodes = selected.stream()
                .map(item -> new MigrationGraphNodeDto(
                        item.getId().toString(),
                        item.getTitle(),
                        pathFor(item, paths),
                        item.getItemType().name(),
                        item.getTargetKey().name(),
                        item.getStatus().name(),
                        item.getRiskLevel().name(),
                        item.getPhase(),
                        item.getPriority()))
                .toList();
        Map<String, EdgeAccumulator> edges = new TreeMap<>();
        for (MigrationDependency dependency : selectedDependencies) {
            String source = dependency.getToItemId().toString();
            String target = dependency.getFromItemId().toString();
            edges.computeIfAbsent(source + "->" + target,
                            ignored -> new EdgeAccumulator(source, target))
                    .add(dependency);
        }
        List<MigrationGraphEdgeDto> edgeDtos = edges.values().stream()
                .map(EdgeAccumulator::toDto)
                .toList();
        boolean truncated = selected.size() < included.size()
                || selectedDependencies.size() < trueEdgeCount;
        return new MigrationGraphDto(plan.getId(), included.size(), (int) Math.min(trueEdgeCount,
                Integer.MAX_VALUE), MAX_GRAPH_ITEMS, MAX_GRAPH_EDGES, truncated, nodes, edgeDtos);
    }

    private MigrationPlanDto planDto(MigrationPlan plan) {
        UUID latestScanId = latestCompletedScan(plan.getProjectId()).map(ScanRun::getId).orElse(null);
        List<MigrationItem> included = itemRepository
                .findByPlanIdAndIncludedTrueOrderByPhaseAscPriorityAscTitleAscIdAsc(plan.getId());
        Map<Integer, int[]> counts = new TreeMap<>();
        for (MigrationItem item : included) {
            int[] bucket = counts.computeIfAbsent(item.getPhase(), ignored -> new int[4]);
            bucket[0]++;
            if (item.getStatus() == MigrationItemStatus.READY) {
                bucket[1]++;
            } else if (item.getStatus() == MigrationItemStatus.REVIEW_REQUIRED) {
                bucket[2]++;
            } else if (item.getStatus() == MigrationItemStatus.BLOCKED) {
                bucket[3]++;
            }
        }
        List<MigrationPhaseDto> phases = counts.entrySet().stream()
                .map(entry -> new MigrationPhaseDto(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2], entry.getValue()[3]))
                .toList();
        return new MigrationPlanDto(
                MigrationPlanSummaryDto.from(plan, isStale(plan, latestScanId)), phases);
    }

    private MigrationItemDetailDto itemDetail(MigrationItem item) {
        List<MigrationItemFinding> links = itemFindingRepository.findByItemIdOrderByRoleAsc(item.getId());
        Map<UUID, AnalysisFinding> findingsById = new HashMap<>();
        if (!links.isEmpty()) {
            findingRepository.findAllById(links.stream()
                    .map(MigrationItemFinding::getFindingId).toList())
                    .forEach(finding -> findingsById.put(finding.getId(), finding));
        }
        List<FindingDto> findings = links.stream()
                .map(link -> findingsById.get(link.getFindingId()))
                .filter(finding -> finding != null)
                .sorted(Comparator.comparing((AnalysisFinding finding) ->
                                finding.getLineStart() == null ? Integer.MAX_VALUE : finding.getLineStart())
                        .thenComparing(AnalysisFinding::getRuleId))
                .map(FindingDto::from)
                .toList();
        List<MigrationDependency> outgoing =
                dependencyRepository.findByPlanIdAndFromItemIdOrderByEvidencePathAscLineStartAsc(
                        item.getPlanId(), item.getId());
        List<MigrationDependency> incoming =
                dependencyRepository.findByPlanIdAndToItemIdOrderByEvidencePathAscLineStartAsc(
                        item.getPlanId(), item.getId());
        Set<UUID> relatedIds = new HashSet<>();
        outgoing.forEach(dependency -> relatedIds.add(dependency.getToItemId()));
        incoming.forEach(dependency -> relatedIds.add(dependency.getFromItemId()));
        Map<UUID, String> titles = new HashMap<>();
        if (!relatedIds.isEmpty()) {
            itemRepository.findAllById(relatedIds)
                    .forEach(related -> titles.put(related.getId(), related.getTitle()));
        }
        List<MigrationDependencyDto> dependencies = outgoing.stream()
                .map(dependency -> MigrationDependencyDto.from(dependency, item.getTitle(),
                        titles.getOrDefault(dependency.getToItemId(), "")))
                .toList();
        List<MigrationDependencyDto> dependents = incoming.stream()
                .map(dependency -> MigrationDependencyDto.from(dependency,
                        titles.getOrDefault(dependency.getFromItemId(), ""), item.getTitle()))
                .toList();
        Map<UUID, String> paths = filePaths(List.of(item));
        return new MigrationItemDetailDto(
                MigrationItemDto.from(item, pathFor(item, paths)), findings, dependencies, dependents);
    }

    private void assignPhases(List<MigrationItem> items, List<MigrationDependency> dependencies) {
        Map<UUID, MigrationItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        Map<UUID, Integer> remaining = new HashMap<>();
        Map<UUID, List<UUID>> dependents = new HashMap<>();
        for (MigrationDependency dependency : dependencies) {
            if (!byId.containsKey(dependency.getFromItemId())
                    || !byId.containsKey(dependency.getToItemId())) {
                continue;
            }
            remaining.merge(dependency.getFromItemId(), 1, Integer::sum);
            dependents.computeIfAbsent(dependency.getToItemId(), ignored -> new ArrayList<>())
                    .add(dependency.getFromItemId());
        }
        List<UUID> queue = new ArrayList<>();
        for (MigrationItem item : items) {
            if (remaining.getOrDefault(item.getId(), 0) == 0) {
                item.placeInPhase(1);
                queue.add(item.getId());
            }
        }
        int processed = 0;
        int maxPhase = 1;
        for (int index = 0; index < queue.size(); index++) {
            MigrationItem item = byId.get(queue.get(index));
            if (item == null) {
                continue;
            }
            processed++;
            maxPhase = Math.max(maxPhase, item.getPhase());
            for (UUID dependentId : dependents.getOrDefault(item.getId(), List.of())) {
                MigrationItem dependent = byId.get(dependentId);
                if (dependent == null) {
                    continue;
                }
                dependent.placeInPhase(Math.max(dependent.getPhase(), item.getPhase() + 1));
                int next = remaining.getOrDefault(dependentId, 1) - 1;
                remaining.put(dependentId, next);
                if (next == 0) {
                    queue.add(dependentId);
                }
            }
        }
        if (processed < items.size()) {
            int cyclePhase = maxPhase + 1;
            for (MigrationItem item : items) {
                if (remaining.getOrDefault(item.getId(), 0) > 0) {
                    item.placeInPhase(cyclePhase);
                    if (item.getStatus() != MigrationItemStatus.BLOCKED) {
                        item.requireReview("DEPENDENCY_CYCLE");
                    }
                }
            }
        }
        for (MigrationItem item : items) {
            item.setPriority(MigrationPlanner.priority(item.getStatus(), item.getRiskLevel()));
        }
    }

    private void updateItemCounts(Iterable<MigrationItem> items, List<MigrationItemFinding> links,
                                  List<MigrationDependency> dependencies) {
        Map<UUID, Integer> findings = new HashMap<>();
        for (MigrationItemFinding link : links) {
            findings.merge(link.getItemId(), 1, Integer::sum);
        }
        Map<UUID, Integer> outgoing = new HashMap<>();
        Map<UUID, Integer> incoming = new HashMap<>();
        for (MigrationDependency dependency : dependencies) {
            outgoing.merge(dependency.getFromItemId(), 1, Integer::sum);
            incoming.merge(dependency.getToItemId(), 1, Integer::sum);
        }
        for (MigrationItem item : items) {
            item.updateGraphCounts(findings.getOrDefault(item.getId(), 0),
                    outgoing.getOrDefault(item.getId(), 0),
                    incoming.getOrDefault(item.getId(), 0));
        }
    }

    private void refreshPlanCounts(MigrationPlan plan, List<MigrationItem> items,
                                   List<MigrationDependency> dependencies) {
        Set<UUID> includedIds = new HashSet<>();
        int included = 0;
        int ready = 0;
        int review = 0;
        int blocked = 0;
        Set<Integer> phases = new TreeSet<>();
        for (MigrationItem item : items) {
            if (!item.isIncluded()) {
                continue;
            }
            includedIds.add(item.getId());
            included++;
            phases.add(item.getPhase());
            if (item.getStatus() == MigrationItemStatus.READY) {
                ready++;
            } else if (item.getStatus() == MigrationItemStatus.REVIEW_REQUIRED) {
                review++;
            } else if (item.getStatus() == MigrationItemStatus.BLOCKED) {
                blocked++;
            }
        }
        long dependencyCount = dependencies.stream()
                .filter(dependency -> includedIds.contains(dependency.getFromItemId())
                        && includedIds.contains(dependency.getToItemId()))
                .count();
        plan.updateCounts(items.size(), included, ready, review, blocked,
                (int) Math.min(dependencyCount, Integer.MAX_VALUE), phases.size());
    }

    private MigrationPlan requirePlan(UUID projectId, UUID planId) {
        requireProject(projectId);
        return planRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MIGRATION_PLAN_NOT_FOUND",
                        "error.migration.planNotFound"));
    }

    private MigrationItem requireItem(UUID planId, UUID itemId) {
        return itemRepository.findByIdAndPlanId(itemId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MIGRATION_ITEM_NOT_FOUND",
                        "error.migration.itemNotFound"));
    }

    private ScanRun resolveScanForPlan(AnalysisProject project, UUID scanId) {
        if (scanId == null) {
            return latestCompletedScan(project.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                            "MIGRATION_SCAN_REQUIRED", "error.migration.scanRequired"));
        }
        ScanRun scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                        "error.scan.notFound"));
        if (!scan.getProjectId().equals(project.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", "error.scan.notFound");
        }
        if (scan.getStatus() != ScanStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "MIGRATION_SCAN_NOT_COMPLETED",
                    "error.migration.scanNotCompleted");
        }
        return scan;
    }

    private java.util.Optional<ScanRun> latestCompletedScan(UUID projectId) {
        return scanRepository.findFirstByProjectIdAndStatusOrderBySequenceDesc(projectId,
                ScanStatus.COMPLETED);
    }

    private MigrationTargetProfile targetProfile(String value) {
        if (value == null || value.isBlank()) {
            return MigrationTargetProfile.SPRING_BOOT_MVC;
        }
        try {
            return MigrationTargetProfile.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MIGRATION_TARGET_UNSUPPORTED", "error.migration.unsupportedTarget");
        }
    }

    private MigrationItemStatus parseStatus(String value) {
        try {
            return MigrationItemStatus.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "error.validation",
                    "status");
        }
    }

    private MigrationItemType parseType(String value) {
        try {
            return MigrationItemType.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "error.validation",
                    "itemType");
        }
    }

    private boolean isStale(MigrationPlan plan, UUID latestScanId) {
        return latestScanId == null || !latestScanId.equals(plan.getScanId());
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                    "error.project.notFound");
        }
    }

    private AnalysisProject requireProjectEntity(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(200, size));
    }

    private Map<UUID, String> filePaths(List<MigrationItem> items) {
        Set<UUID> ids = new HashSet<>();
        for (MigrationItem item : items) {
            if (item.getPrimaryFileId() != null) {
                ids.add(item.getPrimaryFileId());
            }
        }
        Map<UUID, String> result = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ProjectFile file : fileRepository.findAllById(ids)) {
                result.put(file.getId(), file.getRelativePath());
            }
        }
        return result;
    }

    private String pathFor(MigrationItem item, Map<UUID, String> paths) {
        return item.getPrimaryFileId() == null ? null : paths.get(item.getPrimaryFileId());
    }

    private static final class EdgeAccumulator {
        private final String source;
        private final String target;
        private final Set<String> relationTypes = new TreeSet<>();
        private final Set<String> certainties = new TreeSet<>();
        private int count;

        private EdgeAccumulator(String source, String target) {
            this.source = source;
            this.target = target;
        }

        private void add(MigrationDependency dependency) {
            count++;
            relationTypes.add(dependency.getRelationType().name());
            certainties.add(dependency.getCertainty().name());
        }

        private MigrationGraphEdgeDto toDto() {
            return new MigrationGraphEdgeDto(source + "->" + target, source, target,
                    List.copyOf(relationTypes), List.copyOf(certainties), count);
        }
    }
}
