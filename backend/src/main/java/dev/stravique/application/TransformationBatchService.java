package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.TransformationBatchDto;
import dev.stravique.api.dto.TransformationBatchItemDto;
import dev.stravique.api.dto.TransformationProposalDto;
import dev.stravique.api.dto.TransformationRunDto;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.TransformationRun;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.TransformationRunRepository;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransformationBatchService {

    private final TransformationService transformationService;
    private final AuditService auditService;
    private final MessageSource messageSource;
    private final MigrationItemRepository itemRepository;
    private final TransformationRunRepository runRepository;

    public TransformationBatchService(TransformationService transformationService,
                                      AuditService auditService,
                                      MessageSource messageSource,
                                      MigrationItemRepository itemRepository,
                                      TransformationRunRepository runRepository) {
        this.transformationService = transformationService;
        this.auditService = auditService;
        this.messageSource = messageSource;
        this.itemRepository = itemRepository;
        this.runRepository = runRepository;
    }

    public TransformationBatchDto preview(UUID projectId, UUID planId, List<UUID> itemIds,
                                          Locale locale) {
        List<UUID> ids = normalize(itemIds);
        UUID batchId = UUID.randomUUID();
        return transformationService.withProjectLock(projectId, () -> {
            List<TransformationBatchItemDto> items = new ArrayList<>();
            int failed = 0;
            for (UUID itemId : ids) {
                try {
                    TransformationProposalDto proposal = transformationService.propose(
                            projectId, planId, itemId);
                    items.add(TransformationBatchItemDto.success(itemId, title(itemId), "PROPOSED",
                            proposal, null));
                } catch (ApiException exception) {
                    failed++;
                    items.add(failure(itemId, title(itemId), exception, locale));
                }
            }
            String status = failed == 0 ? "PREVIEWED"
                    : failed == ids.size() ? "FAILED" : "PARTIAL";
            auditService.record(projectId, "TRANSFORMATION_BATCH_PREVIEWED",
                    "TRANSFORMATION_BATCH", batchId.toString(),
                    ids.size() + " items, " + failed + " failed");
            return new TransformationBatchDto(batchId, status, ids.size(), 0, 0,
                    failed, 0, items);
        });
    }

    public TransformationBatchDto apply(UUID projectId, UUID planId, List<UUID> itemIds,
                                        boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        List<UUID> ids = normalize(itemIds);
        UUID batchId = UUID.randomUUID();
        return transformationService.withProjectLock(projectId, () -> {
            List<TransformationBatchItemDto> items = new ArrayList<>();
            List<TransformationRunDto> appliedRuns = new ArrayList<>();
            int failed = 0;
            int restoredOnFailure = 0;
            String status = "APPLIED";
            for (int position = 0; position < ids.size(); position++) {
                UUID itemId = ids.get(position);
                try {
                    TransformationProposalDto proposal = transformationService.propose(
                            projectId, planId, itemId);
                    TransformationRunDto run = transformationService.apply(
                            projectId, proposal.id(), true);
                    items.add(TransformationBatchItemDto.success(itemId, title(itemId), "APPLIED",
                            proposal, run));
                    appliedRuns.add(run);
                } catch (ApiException exception) {
                    failed = 1;
                    status = "FAILED";
                    items.add(failure(itemId, title(itemId), exception, locale));
                    for (int index = appliedRuns.size() - 1; index >= 0; index--) {
                        TransformationRunDto applied = appliedRuns.get(index);
                        try {
                            TransformationRunDto restored = transformationService.restore(
                                    projectId, applied.id(), true);
                            markRolledBack(items, applied.id(), restored, null);
                            restoredOnFailure++;
                        } catch (ApiException rollbackException) {
                            status = "ROLLBACK_FAILED";
                            markRolledBack(items, applied.id(), applied,
                                    message(rollbackException, locale));
                        }
                    }
                    for (int rest = position + 1; rest < ids.size(); rest++) {
                        items.add(TransformationBatchItemDto.notAttempted(ids.get(rest),
                                title(ids.get(rest))));
                    }
                    break;
                }
            }
            auditService.record(projectId, "TRANSFORMATION_BATCH_APPLIED",
                    "TRANSFORMATION_BATCH", batchId.toString(),
                    ids.size() + " requested, " + appliedRuns.size() + " applied, "
                            + restoredOnFailure + " restored on failure, status " + status);
            return new TransformationBatchDto(batchId, status, ids.size(),
                    appliedRuns.size(), 0, failed, restoredOnFailure, items);
        });
    }

    public TransformationBatchDto restore(UUID projectId, UUID planId, List<UUID> runIds,
                                          boolean confirmed, Locale locale) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        List<UUID> ids = normalize(runIds);
        UUID batchId = UUID.randomUUID();
        return transformationService.withProjectLock(projectId, () -> {
            List<TransformationBatchItemDto> items = new ArrayList<>();
            int restored = 0;
            int failed = 0;
            for (UUID runId : ids) {
                TransformationRun candidate = runRepository
                        .findByIdAndProjectId(runId, projectId)
                        .orElse(null);
                if (candidate == null || !planId.equals(candidate.getPlanId())) {
                    failed++;
                    items.add(TransformationBatchItemDto.failureForRun(runId,
                            "TRANSFORMATION_RUN_NOT_FOUND",
                            messageSource.getMessage("error.transformation.runNotFound",
                                    null, "error.transformation.runNotFound", locale)));
                    continue;
                }
                try {
                    TransformationRunDto run = transformationService.restore(
                            projectId, runId, true);
                    items.add(TransformationBatchItemDto.success(run.itemId(), title(run.itemId()),
                            "RESTORED", null, run));
                    restored++;
                } catch (ApiException exception) {
                    failed++;
                    items.add(TransformationBatchItemDto.failureForRun(runId,
                            exception.getCode(), message(exception, locale)));
                }
            }
            String status = failed == 0 ? "RESTORED"
                    : restored == 0 ? "FAILED" : "PARTIAL";
            auditService.record(projectId, "TRANSFORMATION_BATCH_RESTORED",
                    "TRANSFORMATION_BATCH", batchId.toString(),
                    ids.size() + " requested, " + restored + " restored, " + failed + " failed");
            return new TransformationBatchDto(batchId, status, ids.size(), 0,
                    restored, failed, 0, items);
        });
    }

    private void markRolledBack(List<TransformationBatchItemDto> items, UUID runId,
                                TransformationRunDto run, String errorMessage) {
        for (int index = 0; index < items.size(); index++) {
            TransformationBatchItemDto item = items.get(index);
            if (runId.equals(item.runId())) {
                items.set(index, new TransformationBatchItemDto(item.itemId(), item.title(),
                        runId, errorMessage == null ? "ROLLED_BACK" : "ROLLBACK_FAILED",
                        item.proposal(), run, errorMessage == null ? null : "ROLLBACK_FAILED",
                        errorMessage));
            }
        }
    }

    private TransformationBatchItemDto failure(UUID itemId, String title,
                                               ApiException exception, Locale locale) {
        return TransformationBatchItemDto.failure(itemId, title, exception.getCode(),
                message(exception, locale));
    }

    private String title(UUID itemId) {
        return itemRepository.findById(itemId)
                .map(MigrationItem::getTitle)
                .orElse(itemId.toString());
    }

    private String message(ApiException exception, Locale locale) {
        return messageSource.getMessage(exception.getMessageKey(), exception.getArgs(),
                exception.getMessageKey(), locale);
    }

    private List<UUID> normalize(List<UUID> ids) {
        List<UUID> normalized = new ArrayList<>(new LinkedHashSet<>(ids));
        normalized.removeIf(Objects::isNull);
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_BATCH_EMPTY", "error.transformation.batchEmpty");
        }
        return normalized;
    }
}
