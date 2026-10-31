package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.TransformationCoverageDto;
import dev.stravique.api.dto.TransformationCoverageItemDto;
import dev.stravique.api.dto.TransformationCoverageSummaryDto;
import dev.stravique.api.dto.TransformationPlanRunDto;
import dev.stravique.api.dto.TransformationPlanRunItemDto;
import dev.stravique.api.dto.TransformationPlanRunSummaryDto;
import dev.stravique.api.dto.TransformationProposalDto;
import dev.stravique.api.dto.TransformationRunDto;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemStatus;
import dev.stravique.domain.MigrationPlan;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationPlanRun;
import dev.stravique.domain.TransformationPlanRunItem;
import dev.stravique.domain.TransformationPlanRunItemStatus;
import dev.stravique.domain.TransformationPlanRunStatus;
import dev.stravique.domain.TransformationProposal;
import dev.stravique.domain.TransformationProposalStatus;
import dev.stravique.domain.TransformationRun;
import dev.stravique.domain.TransformationRunStatus;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.MigrationPlanRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.TransformationPlanRunItemRepository;
import dev.stravique.infrastructure.TransformationPlanRunRepository;
import dev.stravique.infrastructure.TransformationProposalRepository;
import dev.stravique.infrastructure.TransformationRunRepository;
import dev.stravique.transformation.RuleDescriptor;
import dev.stravique.transformation.TransformationEngine;
import org.springframework.context.MessageSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransformationPlanRunService {

    private static final int PLAN_RUN_CHUNK_SIZE = 50;

    private final AnalysisProjectRepository projectRepository;
    private final MigrationPlanRepository planRepository;
    private final MigrationItemRepository itemRepository;
    private final ProjectFileRepository fileRepository;
    private final TransformationProposalRepository proposalRepository;
    private final TransformationRunRepository runRepository;
    private final TransformationPlanRunRepository planRunRepository;
    private final TransformationPlanRunItemRepository planRunItemRepository;
    private final TransformationEngine engine;
    private final TransformationService transformationService;
    private final TaskExecutor planRunExecutor;
    private final AuditService auditService;
    private final MessageSource messageSource;
    private final Set<UUID> activePlanRuns = ConcurrentHashMap.newKeySet();

    public TransformationPlanRunService(AnalysisProjectRepository projectRepository,
                                        MigrationPlanRepository planRepository,
                                        MigrationItemRepository itemRepository,
                                        ProjectFileRepository fileRepository,
                                        TransformationProposalRepository proposalRepository,
                                        TransformationRunRepository runRepository,
                                        TransformationPlanRunRepository planRunRepository,
                                        TransformationPlanRunItemRepository planRunItemRepository,
                                        TransformationEngine engine,
                                        TransformationService transformationService,
                                        TaskExecutor planRunExecutor,
                                        AuditService auditService,
                                        MessageSource messageSource) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.fileRepository = fileRepository;
        this.proposalRepository = proposalRepository;
        this.runRepository = runRepository;
        this.planRunRepository = planRunRepository;
        this.planRunItemRepository = planRunItemRepository;
        this.engine = engine;
        this.transformationService = transformationService;
        this.planRunExecutor = planRunExecutor;
        this.auditService = auditService;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public TransformationCoverageDto coverage(UUID projectId, UUID planId) {
        MigrationPlan plan = requirePlan(projectId, planId);
        return computeCoverage(plan).dto();
    }

    public TransformationPlanRunDto apply(UUID projectId, UUID planId, boolean confirmed,
                                          Locale locale) {
        return apply(projectId, planId, confirmed, null, locale);
    }

    public TransformationPlanRunDto apply(UUID projectId, UUID planId, boolean confirmed,
                                          List<UUID> itemIds, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        return transformationService.withProjectLock(projectId, () -> {
            MigrationPlan plan = requirePlan(projectId, planId);
            if (planRunRepository.existsByProjectIdAndPlanIdAndStatus(projectId, planId,
                    TransformationPlanRunStatus.RUNNING)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_ACTIVE",
                        "error.transformation.planRunActive");
            }
            Coverage coverage = computeCoverage(plan);
            List<MigrationItem> eligible = coverage.eligibleItems();
            if (itemIds != null && !itemIds.isEmpty()) {
                Set<UUID> selected = new LinkedHashSet<>();
                for (UUID itemId : itemIds) {
                    if (itemId != null) {
                        selected.add(itemId);
                    }
                }
                if (!selected.isEmpty()) {
                    Set<UUID> eligibleIds = new LinkedHashSet<>();
                    eligible.forEach(item -> eligibleIds.add(item.getId()));
                    if (!eligibleIds.containsAll(selected)) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "TRANSFORMATION_PLAN_ITEMS_INVALID",
                                "error.transformation.planItemsInvalid");
                    }
                    eligible = eligible.stream()
                            .filter(item -> selected.contains(item.getId()))
                            .toList();
                }
            }
            if (eligible.isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRANSFORMATION_PLAN_EMPTY", "error.transformation.planEmpty");
            }
            TransformationPlanRun planRun = planRunRepository.save(
                    TransformationPlanRun.started(projectId, planId, eligible.size()));
            List<TransformationPlanRunItem> planned = new ArrayList<>();
            for (int index = 0; index < eligible.size(); index++) {
                planned.add(TransformationPlanRunItem.planned(planRun.getId(), projectId, planId,
                        eligible.get(index).getId(), index + 1));
            }
            List<TransformationPlanRunItem> children = planRunItemRepository.saveAll(planned);
            UUID planRunId = planRun.getId();
            activePlanRuns.add(planRunId);
            try {
                planRunExecutor.execute(() -> executePlanRun(projectId, planId, planRunId, locale));
            } catch (RuntimeException exception) {
                activePlanRuns.remove(planRunId);
                planRun.markFailed(0, 0, false);
                planRunRepository.save(planRun);
                auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_FAILED",
                        "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                        "task submission failed");
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "TRANSFORMATION_PLAN_RUN_FAILED",
                        "error.transformation.planRunFailed");
            }
            return toDto(planRun, children, itemTitles(planId));
        });
    }

    public TransformationPlanRunDto cancel(UUID projectId, UUID planId, UUID planRunId,
                                           boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        requirePlan(projectId, planId);
        TransformationPlanRun planRun = requirePlanRun(projectId, planId, planRunId);
        if (planRun.getStatus() != TransformationPlanRunStatus.RUNNING) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "TRANSFORMATION_PLAN_RUN_NOT_CANCELLABLE",
                    "error.transformation.planRunNotCancellable");
        }
        if (activePlanRuns.contains(planRunId)) {
            planRun.requestCancel();
            planRunRepository.save(planRun);
            auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_CANCEL_REQUESTED",
                    "TRANSFORMATION_PLAN_RUN", planRunId.toString(), "cooperative cancel");
            return toDto(planRun,
                    planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId),
                    itemTitles(planId));
        }
        return transformationService.withProjectLock(projectId, () -> {
            TransformationPlanRun current = requirePlanRun(projectId, planId, planRunId);
            if (current.getStatus() != TransformationPlanRunStatus.RUNNING) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_CANCELLABLE",
                        "error.transformation.planRunNotCancellable");
            }
            List<TransformationPlanRunItem> children =
                    planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
            RollbackOutcome outcome = rollbackAppliedChildren(projectId, children, locale);
            current.markCancelled(outcome.applied(), outcome.rolledBack(), outcome.failed());
            planRunRepository.save(current);
            auditService.record(projectId,
                    outcome.failed()
                            ? "TRANSFORMATION_PLAN_RUN_ROLLBACK_FAILED"
                            : "TRANSFORMATION_PLAN_RUN_CANCELLED",
                    "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                    outcome.applied() + " applied, " + outcome.rolledBack() + " rolled back");
            return toDto(current, children, itemTitles(planId));
        });
    }

    private void executePlanRun(UUID projectId, UUID planId, UUID planRunId, Locale locale) {
        try {
            transformationService.withProjectLock(projectId, () -> {
                runPlanItems(projectId, planId, planRunId, locale);
                return null;
            });
        } catch (RuntimeException exception) {
            failUnexpected(projectId, planId, planRunId, locale);
        } finally {
            activePlanRuns.remove(planRunId);
        }
    }

    private void runPlanItems(UUID projectId, UUID planId, UUID planRunId, Locale locale) {
        TransformationPlanRun planRun = planRunRepository.findById(planRunId).orElse(null);
        if (planRun == null || planRun.getStatus() != TransformationPlanRunStatus.RUNNING) {
            return;
        }
        List<TransformationPlanRunItem> children =
                planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
        for (int chunkStart = 0; chunkStart < children.size();
             chunkStart += PLAN_RUN_CHUNK_SIZE) {
            int chunkEnd = Math.min(children.size(), chunkStart + PLAN_RUN_CHUNK_SIZE);
            for (int index = chunkStart; index < chunkEnd; index++) {
                if (cancellationRequested(planRunId)) {
                    finishCancelled(projectId, planId, planRunId, children, locale);
                    return;
                }
                TransformationPlanRunItem child = children.get(index);
                if (child.getStatus() == TransformationPlanRunItemStatus.APPLIED) {
                    continue;
                }
                if (child.getStatus() != TransformationPlanRunItemStatus.NOT_ATTEMPTED) {
                    finishFailed(projectId, planId, planRunId, children, locale);
                    return;
                }
                MigrationItem item = itemRepository.findById(child.getItemId()).orElse(null);
                try {
                    if (item == null) {
                        throw new ApiException(HttpStatus.NOT_FOUND,
                                "MIGRATION_ITEM_NOT_FOUND", "error.migration.itemNotFound");
                    }
                    TransformationProposalDto proposal = transformationService.propose(
                            projectId, planId, item.getId());
                    TransformationRunDto run = transformationService.apply(
                            projectId, proposal.id(), true);
                    child.markApplied(run.id());
                    planRunItemRepository.save(child);
                } catch (ApiException exception) {
                    child.markFailed(exception.getCode(), message(exception, locale));
                    planRunItemRepository.save(child);
                    finishFailed(projectId, planId, planRunId, children, locale);
                    return;
                }
            }
        }
        if (cancellationRequested(planRunId)) {
            finishCancelled(projectId, planId, planRunId, children, locale);
            return;
        }
        TransformationPlanRun current = planRunRepository.findById(planRunId).orElse(null);
        if (current == null || current.getStatus() != TransformationPlanRunStatus.RUNNING) {
            return;
        }
        int applied = (int) children.stream()
                .filter(child -> child.getStatus() == TransformationPlanRunItemStatus.APPLIED)
                .count();
        current.markApplied(applied);
        planRunRepository.save(current);
        auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_APPLIED",
                "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                children.size() + " eligible, " + applied + " applied");
    }

    private void finishFailed(UUID projectId, UUID planId, UUID planRunId,
                              List<TransformationPlanRunItem> children, Locale locale) {
        RollbackOutcome outcome = rollbackAppliedChildren(projectId, children, locale);
        TransformationPlanRun current = planRunRepository.findById(planRunId).orElse(null);
        if (current == null || current.getStatus() != TransformationPlanRunStatus.RUNNING) {
            return;
        }
        current.markFailed(outcome.applied(), outcome.rolledBack(), outcome.failed());
        planRunRepository.save(current);
        auditService.record(projectId,
                outcome.failed()
                        ? "TRANSFORMATION_PLAN_RUN_ROLLBACK_FAILED"
                        : "TRANSFORMATION_PLAN_RUN_FAILED",
                "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                children.size() + " eligible, " + outcome.applied() + " applied, "
                        + outcome.rolledBack() + " rolled back");
    }

    private void finishCancelled(UUID projectId, UUID planId, UUID planRunId,
                                 List<TransformationPlanRunItem> children, Locale locale) {
        RollbackOutcome outcome = rollbackAppliedChildren(projectId, children, locale);
        TransformationPlanRun current = planRunRepository.findById(planRunId).orElse(null);
        if (current == null || current.getStatus() != TransformationPlanRunStatus.RUNNING) {
            return;
        }
        current.markCancelled(outcome.applied(), outcome.rolledBack(), outcome.failed());
        planRunRepository.save(current);
        auditService.record(projectId,
                outcome.failed()
                        ? "TRANSFORMATION_PLAN_RUN_ROLLBACK_FAILED"
                        : "TRANSFORMATION_PLAN_RUN_CANCELLED",
                "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                outcome.applied() + " applied, " + outcome.rolledBack() + " rolled back");
    }

    private void failUnexpected(UUID projectId, UUID planId, UUID planRunId, Locale locale) {
        TransformationPlanRun current = planRunRepository.findById(planRunId).orElse(null);
        if (current == null || current.getStatus() != TransformationPlanRunStatus.RUNNING) {
            return;
        }
        List<TransformationPlanRunItem> children =
                planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
        RollbackOutcome outcome = rollbackAppliedChildren(projectId, children, locale);
        current.markFailed(outcome.applied(), outcome.rolledBack(), outcome.failed());
        planRunRepository.save(current);
        auditService.record(projectId,
                outcome.failed()
                        ? "TRANSFORMATION_PLAN_RUN_ROLLBACK_FAILED"
                        : "TRANSFORMATION_PLAN_RUN_FAILED",
                "TRANSFORMATION_PLAN_RUN", planRunId.toString(),
                "unexpected failure, " + outcome.applied() + " applied, "
                        + outcome.rolledBack() + " rolled back");
    }

    private RollbackOutcome rollbackAppliedChildren(UUID projectId,
                                                    List<TransformationPlanRunItem> children,
                                                    Locale locale) {
        List<TransformationPlanRunItem> appliedChildren = children.stream()
                .filter(child -> child.getStatus() == TransformationPlanRunItemStatus.APPLIED)
                .sorted(Comparator.comparingInt(TransformationPlanRunItem::getOrdinal).reversed())
                .toList();
        int rolledBack = 0;
        boolean rollbackFailed = false;
        for (TransformationPlanRunItem child : appliedChildren) {
            try {
                transformationService.restore(projectId, child.getRunId(), true);
                child.markRolledBack();
                rolledBack++;
            } catch (ApiException exception) {
                child.markRollbackFailed(exception.getCode(), message(exception, locale));
                rollbackFailed = true;
            }
            planRunItemRepository.save(child);
        }
        return new RollbackOutcome(appliedChildren.size(), rolledBack, rollbackFailed);
    }

    private boolean cancellationRequested(UUID planRunId) {
        return planRunRepository.findById(planRunId)
                .map(run -> run.isCancelRequested()
                        || run.getStatus() != TransformationPlanRunStatus.RUNNING)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<TransformationPlanRunSummaryDto> history(UUID projectId, UUID planId) {
        requirePlan(projectId, planId);
        return planRunRepository.findByProjectIdAndPlanIdOrderByCreatedAtDesc(projectId, planId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransformationPlanRunDto detail(UUID projectId, UUID planId, UUID planRunId) {
        requirePlan(projectId, planId);
        TransformationPlanRun planRun = requirePlanRun(projectId, planId, planRunId);
        return toDto(planRun, planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId),
                itemTitles(planId));
    }

    public TransformationPlanRunDto restore(UUID projectId, UUID planId, UUID planRunId,
                                            boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        return transformationService.withProjectLock(projectId, () -> {
            requirePlan(projectId, planId);
            TransformationPlanRun planRun = requirePlanRun(projectId, planId, planRunId);
            if (planRun.getStatus() == TransformationPlanRunStatus.RUNNING
                    || planRun.getStatus() == TransformationPlanRunStatus.INTERRUPTED
                    || planRun.getStatus() == TransformationPlanRunStatus.RESTORED) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_RESTORABLE",
                        "error.transformation.planRunNotRestorable");
            }
            List<TransformationPlanRunItem> children =
                    planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
            List<TransformationPlanRunItem> applied = children.stream()
                    .filter(child -> child.getStatus() == TransformationPlanRunItemStatus.APPLIED)
                    .sorted(Comparator.comparingInt(TransformationPlanRunItem::getOrdinal).reversed())
                    .toList();
            if (applied.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_RESTORABLE",
                        "error.transformation.planRunNotRestorable");
            }
            int restored = 0;
            int failed = 0;
            boolean rollbackFailed = false;
            for (TransformationPlanRunItem child : applied) {
                try {
                    transformationService.restore(projectId, child.getRunId(), true);
                    child.markRestored();
                    restored++;
                } catch (ApiException exception) {
                    child.markRollbackFailed(exception.getCode(), message(exception, locale));
                    failed++;
                    rollbackFailed = true;
                }
                planRunItemRepository.save(child);
            }
            planRun.markRestored(restored, failed, rollbackFailed);
            planRunRepository.save(planRun);
            auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_RESTORED",
                    "TRANSFORMATION_PLAN_RUN", planRun.getId().toString(),
                    applied.size() + " planned, " + restored + " restored, " + failed
                            + " failed");
            return toDto(planRun, children, itemTitles(planId));
        });
    }

    public TransformationPlanRunDto resume(UUID projectId, UUID planId, UUID planRunId,
                                           boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        return transformationService.withProjectLock(projectId, () -> {
            requirePlan(projectId, planId);
            if (planRunRepository.existsByProjectIdAndPlanIdAndStatus(projectId, planId,
                    TransformationPlanRunStatus.RUNNING)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_ACTIVE",
                        "error.transformation.planRunActive");
            }
            TransformationPlanRun planRun = requirePlanRun(projectId, planId, planRunId);
            if (planRun.getStatus() != TransformationPlanRunStatus.INTERRUPTED) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_RESUMABLE",
                        "error.transformation.planRunNotResumable");
            }
            List<TransformationPlanRunItem> children =
                    planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
            boolean resumable = children.stream().allMatch(child ->
                    child.getStatus() == TransformationPlanRunItemStatus.APPLIED
                            || child.getStatus() == TransformationPlanRunItemStatus.NOT_ATTEMPTED);
            if (!resumable) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_RESUMABLE",
                        "error.transformation.planRunNotResumable");
            }
            planRun.resumeFromInterrupted();
            planRunRepository.save(planRun);
            UUID planRunIdValue = planRun.getId();
            activePlanRuns.add(planRunIdValue);
            try {
                planRunExecutor.execute(() -> executePlanRun(projectId, planId, planRunIdValue,
                        locale));
            } catch (RuntimeException exception) {
                activePlanRuns.remove(planRunIdValue);
                planRun.markFailed(0, 0, false);
                planRunRepository.save(planRun);
                auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_FAILED",
                        "TRANSFORMATION_PLAN_RUN", planRunIdValue.toString(),
                        "resume task submission failed");
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "TRANSFORMATION_PLAN_RUN_FAILED",
                        "error.transformation.planRunFailed");
            }
            auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_RESUMED",
                    "TRANSFORMATION_PLAN_RUN", planRunIdValue.toString(),
                    children.size() + " items, resumed from interrupted run");
            return toDto(planRun, children, itemTitles(planId));
        });
    }

    public TransformationPlanRunDto recover(UUID projectId, UUID planId, UUID planRunId,
                                            boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        return transformationService.withProjectLock(projectId, () -> {
            requirePlan(projectId, planId);
            TransformationPlanRun planRun = requirePlanRun(projectId, planId, planRunId);
            if (planRun.getStatus() != TransformationPlanRunStatus.INTERRUPTED) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_RUN_NOT_RECOVERABLE",
                        "error.transformation.planRunNotRecoverable");
            }
            List<TransformationPlanRunItem> children =
                    planRunItemRepository.findByPlanRunIdOrderByOrdinalAsc(planRunId);
            List<TransformationPlanRunItem> applied = children.stream()
                    .filter(child -> child.getStatus() == TransformationPlanRunItemStatus.APPLIED)
                    .sorted(Comparator.comparingInt(TransformationPlanRunItem::getOrdinal).reversed())
                    .toList();
            int restored = 0;
            int failed = 0;
            boolean rollbackFailed = false;
            for (TransformationPlanRunItem child : applied) {
                try {
                    transformationService.restore(projectId, child.getRunId(), true);
                    child.markRestored();
                    restored++;
                } catch (ApiException exception) {
                    child.markRollbackFailed(exception.getCode(), message(exception, locale));
                    failed++;
                    rollbackFailed = true;
                }
                planRunItemRepository.save(child);
            }
            planRun.markRecovered(applied.size(), restored, failed, rollbackFailed);
            planRunRepository.save(planRun);
            auditService.record(projectId, "TRANSFORMATION_PLAN_RUN_RECOVERED",
                    "TRANSFORMATION_PLAN_RUN", planRun.getId().toString(),
                    applied.size() + " applied, " + restored + " restored, " + failed
                            + " failed");
            return toDto(planRun, children, itemTitles(planId));
        });
    }

    private Coverage computeCoverage(MigrationPlan plan) {
        List<MigrationItem> items = itemRepository
                .findByPlanIdOrderByPhaseAscPriorityAscTitleAscIdAsc(plan.getId());
        List<UUID> fileIds = items.stream()
                .map(MigrationItem::getPrimaryFileId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, ProjectFile> files = new HashMap<>();
        fileRepository.findAllById(fileIds).forEach(file -> files.put(file.getId(), file));
        Map<UUID, List<TransformationProposal>> proposals = new LinkedHashMap<>();
        for (TransformationProposal proposal : proposalRepository
                .findByPlanIdOrderByCreatedAtAsc(plan.getId())) {
            proposals.computeIfAbsent(proposal.getItemId(), ignored -> new ArrayList<>())
                    .add(proposal);
        }
        Map<UUID, List<TransformationRun>> runs = new LinkedHashMap<>();
        for (TransformationRun run : runRepository.findByPlanIdOrderByCreatedAtAsc(plan.getId())) {
            runs.computeIfAbsent(run.getItemId(), ignored -> new ArrayList<>()).add(run);
        }
        List<TransformationCoverageItemDto> rows = new ArrayList<>();
        List<MigrationItem> eligibleItems = new ArrayList<>();
        int supported = 0;
        int eligible = 0;
        int excluded = 0;
        int reviewRequired = 0;
        int blocked = 0;
        int proposalCount = 0;
        int appliedRuns = 0;
        int restoredRuns = 0;
        for (MigrationItem item : items) {
            ProjectFile file = item.getPrimaryFileId() == null
                    ? null : files.get(item.getPrimaryFileId());
            Optional<RuleDescriptor> rule = file == null
                    ? Optional.empty() : engine.ruleFor(item, file);
            boolean ruleSupported = rule.isPresent();
            String ineligibleReason = null;
            if (!item.isIncluded()) {
                ineligibleReason = "EXCLUDED";
            } else if (item.getStatus() != MigrationItemStatus.READY) {
                ineligibleReason = "NOT_READY";
            } else if (!ruleSupported) {
                ineligibleReason = "NO_RULE";
            } else {
                TransformationProbe probe = transformationService.probe(
                        plan.getProjectId(), plan.getId(), item.getId());
                if (!probe.transformable()) {
                    ineligibleReason = probe.errorCode();
                }
            }
            boolean itemEligible = ineligibleReason == null;
            if (ruleSupported) {
                supported++;
            }
            if (itemEligible) {
                eligible++;
                eligibleItems.add(item);
            }
            if (!item.isIncluded()) {
                excluded++;
            }
            if (item.getStatus() == MigrationItemStatus.REVIEW_REQUIRED) {
                reviewRequired++;
            }
            if (item.getStatus() == MigrationItemStatus.BLOCKED) {
                blocked++;
            }
            List<TransformationProposal> itemProposals =
                    proposals.getOrDefault(item.getId(), List.of());
            List<TransformationRun> itemRuns = runs.getOrDefault(item.getId(), List.of());
            int itemAppliedRuns = (int) itemRuns.stream()
                    .filter(run -> run.getStatus() == TransformationRunStatus.APPLIED)
                    .count();
            int itemRestoredRuns = (int) itemRuns.stream()
                    .filter(run -> run.getStatus() == TransformationRunStatus.RESTORED)
                    .count();
            proposalCount += itemProposals.size();
            appliedRuns += itemAppliedRuns;
            restoredRuns += itemRestoredRuns;
            rows.add(new TransformationCoverageItemDto(
                    item.getId(), item.getTitle(), item.getItemType().name(),
                    item.getTargetKey().name(), item.getStatus().name(), item.isIncluded(),
                    ruleSupported, itemEligible, ineligibleReason,
                    rule.map(RuleDescriptor::ruleId).orElse(null),
                    rule.map(RuleDescriptor::ruleVersion).orElse(null),
                    itemProposals.size(), itemAppliedRuns, itemRestoredRuns,
                    itemProposals.isEmpty() ? null
                            : itemProposals.getLast().getStatus().name(),
                    itemRuns.isEmpty() ? null : itemRuns.getLast().getStatus().name()));
        }
        TransformationCoverageSummaryDto summary = new TransformationCoverageSummaryDto(
                items.size(), supported, items.size() - supported, eligible, excluded,
                reviewRequired, blocked, proposalCount, appliedRuns, restoredRuns);
        return new Coverage(new TransformationCoverageDto(plan.getId(), plan.getScanId(),
                engine.rules().size(), summary, rows), eligibleItems);
    }

    private Map<UUID, String> itemTitles(UUID planId) {
        Map<UUID, String> titles = new HashMap<>();
        for (MigrationItem item : itemRepository
                .findByPlanIdOrderByPhaseAscPriorityAscTitleAscIdAsc(planId)) {
            titles.put(item.getId(), item.getTitle());
        }
        return titles;
    }

    private TransformationPlanRunDto toDto(TransformationPlanRun planRun,
                                           List<TransformationPlanRunItem> items,
                                           Map<UUID, String> titles) {
        List<TransformationPlanRunItemDto> rows = items.stream()
                .sorted(Comparator.comparingInt(TransformationPlanRunItem::getOrdinal))
                .map(item -> new TransformationPlanRunItemDto(
                        item.getItemId(),
                        titles.getOrDefault(item.getItemId(), item.getItemId().toString()),
                        item.getOrdinal(), item.getStatus().name(), item.getRunId(),
                        item.getErrorCode(), item.getErrorMessage()))
                .toList();
        return new TransformationPlanRunDto(planRun.getId(), planRun.getPlanId(),
                planRun.getStatus().name(), planRun.getEligibleCount(),
                planRun.getAppliedCount(), planRun.getFailedCount(),
                planRun.getRolledBackCount(), planRun.getRestoredCount(),
                planRun.isCancelRequested(), planRun.getCreatedAt(),
                planRun.getCompletedAt(), rows);
    }

    private TransformationPlanRunSummaryDto toSummary(TransformationPlanRun planRun) {
        return new TransformationPlanRunSummaryDto(planRun.getId(), planRun.getPlanId(),
                planRun.getStatus().name(), planRun.getEligibleCount(),
                planRun.getAppliedCount(), planRun.getFailedCount(),
                planRun.getRolledBackCount(), planRun.getRestoredCount(),
                planRun.isCancelRequested(), planRun.getCreatedAt(),
                planRun.getCompletedAt());
    }

    private MigrationPlan requirePlan(UUID projectId, UUID planId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
        return planRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "MIGRATION_PLAN_NOT_FOUND", "error.migration.planNotFound"));
    }

    private TransformationPlanRun requirePlanRun(UUID projectId, UUID planId, UUID planRunId) {
        return planRunRepository.findByIdAndProjectIdAndPlanId(planRunId, projectId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TRANSFORMATION_PLAN_RUN_NOT_FOUND",
                        "error.transformation.planRunNotFound"));
    }

    private String message(ApiException exception, Locale locale) {
        return messageSource.getMessage(exception.getMessageKey(), exception.getArgs(),
                exception.getMessageKey(), locale);
    }

    private record Coverage(TransformationCoverageDto dto, List<MigrationItem> eligibleItems) {
    }

    private record RollbackOutcome(int applied, int rolledBack, boolean failed) {
    }
}
