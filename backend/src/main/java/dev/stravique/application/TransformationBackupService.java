package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.TransformationBackupItemDto;
import dev.stravique.api.dto.TransformationBackupLedgerDto;
import dev.stravique.api.dto.TransformationBackupPruneDto;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.TransformationRun;
import dev.stravique.domain.TransformationRunStatus;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.TransformationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TransformationBackupService {

    private final AnalysisProjectRepository projectRepository;
    private final TransformationRunRepository runRepository;
    private final MigrationItemRepository itemRepository;
    private final StraviqueProperties properties;
    private final AuditService auditService;

    public TransformationBackupService(AnalysisProjectRepository projectRepository,
                                       TransformationRunRepository runRepository,
                                       MigrationItemRepository itemRepository,
                                       StraviqueProperties properties,
                                       AuditService auditService) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TransformationBackupLedgerDto ledger(UUID projectId) {
        AnalysisProject project = requireProject(projectId);
        Path projectRoot = projectRoot(project);
        Path backupRoot = backupRoot(projectRoot);
        List<TransformationBackupItemDto> items = new ArrayList<>();
        int existing = 0;
        int restorable = 0;
        int prunable = 0;
        long totalBytes = 0;
        for (TransformationRun run : runRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            if (run.getBackupPath() == null) {
                continue;
            }
            Path backup = path(run.getBackupPath());
            boolean safe = backup != null && backup.startsWith(backupRoot)
                    && !backup.startsWith(projectRoot);
            long size = 0;
            boolean exists = false;
            if (safe) {
                exists = Files.isRegularFile(backup);
                if (exists) {
                    try {
                        size = Files.size(backup);
                    } catch (IOException exception) {
                        exists = false;
                    }
                }
            }
            boolean itemRestorable = exists && run.getStatus() == TransformationRunStatus.APPLIED;
            boolean itemPrunable = exists && run.getStatus() == TransformationRunStatus.RESTORED;
            if (exists) {
                existing++;
                totalBytes += size;
            }
            if (itemRestorable) {
                restorable++;
            }
            if (itemPrunable) {
                prunable++;
            }
            items.add(new TransformationBackupItemDto(run.getId(), run.getPlanId(),
                    run.getItemId(),
                    itemRepository.findById(run.getItemId())
                            .map(item -> item.getTitle()).orElse(null),
                    run.getRelativePath(), run.getStatus().name(), run.getRuleId(),
                    run.getRuleVersion(), exists, size, itemRestorable, itemPrunable,
                    run.getCreatedAt(), run.getFinishedAt()));
        }
        return new TransformationBackupLedgerDto(projectId, items.size(), existing,
                items.size() - existing, restorable, prunable, totalBytes, items);
    }

    public TransformationBackupPruneDto prune(UUID projectId, boolean confirmed) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        AnalysisProject project = requireProject(projectId);
        Path projectRoot = projectRoot(project);
        Path backupRoot = backupRoot(projectRoot);
        int requested = 0;
        int deleted = 0;
        int failed = 0;
        long deletedBytes = 0;
        for (TransformationRun run : runRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            if (run.getBackupPath() == null
                    || run.getStatus() != TransformationRunStatus.RESTORED) {
                continue;
            }
            requested++;
            Path backup = path(run.getBackupPath());
            if (backup == null || !backup.startsWith(backupRoot)
                    || backup.startsWith(projectRoot)) {
                failed++;
                continue;
            }
            if (!Files.isRegularFile(backup)) {
                continue;
            }
            try {
                long size = Files.size(backup);
                Files.delete(backup);
                deleted++;
                deletedBytes += size;
            } catch (IOException exception) {
                failed++;
            }
        }
        auditService.record(projectId, "TRANSFORMATION_BACKUPS_PRUNED",
                "TRANSFORMATION_BACKUP", projectId.toString(),
                requested + " requested, " + deleted + " deleted, " + failed + " failed");
        return new TransformationBackupPruneDto(requested, deleted, failed, deletedBytes);
    }

    private AnalysisProject requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
    }

    private Path projectRoot(AnalysisProject project) {
        return Path.of(project.getSourcePath()).toAbsolutePath().normalize();
    }

    private Path backupRoot(Path projectRoot) {
        String configured = properties.getTransformation().getBackupDirectory();
        Path root = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".stravique",
                        "transformation-backups")
                : Path.of(configured).toAbsolutePath().normalize();
        if (root.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_BACKUP_UNSAFE", "error.transformation.backupUnsafe");
        }
        return root.toAbsolutePath().normalize();
    }

    private Path path(String value) {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
    }
}
