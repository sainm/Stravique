package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.TransformationProposalDto;
import dev.stravique.api.dto.TransformationRunDto;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationPlan;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.domain.TransformationProposal;
import dev.stravique.domain.TransformationProposalStatus;
import dev.stravique.domain.TransformationRun;
import dev.stravique.domain.TransformationRunStatus;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.MigrationPlanRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.infrastructure.TransformationProposalRepository;
import dev.stravique.infrastructure.TransformationRunRepository;
import dev.stravique.inventory.TextDecoder;
import dev.stravique.transformation.TransformationEngine;
import dev.stravique.transformation.TransformationInvalidException;
import dev.stravique.transformation.TransformationOutcome;
import dev.stravique.transformation.TransformationUnsupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransformationService {

    private final AnalysisProjectRepository projectRepository;
    private final MigrationPlanRepository planRepository;
    private final MigrationItemRepository itemRepository;
    private final ProjectFileRepository fileRepository;
    private final ScanRunRepository scanRepository;
    private final TransformationProposalRepository proposalRepository;
    private final TransformationRunRepository runRepository;
    private final TransformationEngine engine;
    private final TextDecoder decoder;
    private final StraviqueProperties properties;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TransformationService(AnalysisProjectRepository projectRepository,
                                 MigrationPlanRepository planRepository,
                                 MigrationItemRepository itemRepository,
                                 ProjectFileRepository fileRepository,
                                 ScanRunRepository scanRepository,
                                 TransformationProposalRepository proposalRepository,
                                 TransformationRunRepository runRepository,
                                 TransformationEngine engine,
                                 TextDecoder decoder,
                                 StraviqueProperties properties,
                                 AuditService auditService,
                                 PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.fileRepository = fileRepository;
        this.scanRepository = scanRepository;
        this.proposalRepository = proposalRepository;
        this.runRepository = runRepository;
        this.engine = engine;
        this.decoder = decoder;
        this.properties = properties;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TransformationProposalDto propose(UUID projectId, UUID planId, UUID itemId) {
        AnalysisProject project = requireProject(projectId);
        MigrationPlan plan = requirePlan(projectId, planId);
        MigrationItem item = requireItem(planId, itemId);
        if (!item.isIncluded()) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_ITEM_EXCLUDED",
                    "error.transformation.itemExcluded");
        }
        requirePlanCurrent(plan);
        ProjectFile file = item.getPrimaryFileId() == null ? null
                : fileRepository.findById(item.getPrimaryFileId()).orElse(null);
        if (file == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_UNSUPPORTED", "error.transformation.unsupported");
        }
        Path root = projectRoot(project);
        Path target = safeSource(root, file.getRelativePath());
        Source source = readSource(target, file);
        TransformationOutcome outcome;
        try {
            outcome = engine.propose(item, file, source.text(), file.getRelativePath());
        } catch (TransformationUnsupportedException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_UNSUPPORTED", "error.transformation.unsupported");
        } catch (TransformationInvalidException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_INVALID", "error.transformation.invalid");
        }
        byte[] proposedBytes = outcome.proposedText().getBytes(source.charset());
        String proposedSha = sha256(proposedBytes);
        for (TransformationProposal previous : proposalRepository
                .findByPlanIdAndItemIdAndStatus(planId, itemId,
                        TransformationProposalStatus.PROPOSED)) {
            previous.supersede();
            proposalRepository.save(previous);
        }
        TransformationProposal proposal = ProposalFactory.create(projectId, planId, itemId,
                file.getId(), file.getRelativePath(), outcome, source.sha256(), proposedSha);
        proposalRepository.save(proposal);
        auditService.record(projectId, "TRANSFORMATION_PROPOSED", "TRANSFORMATION_PROPOSAL",
                proposal.getId().toString(), file.getRelativePath());
        return TransformationProposalDto.from(proposal);
    }

    @Transactional(readOnly = true)
    public List<TransformationProposalDto> proposals(UUID projectId, UUID planId, UUID itemId) {
        requireProject(projectId);
        requirePlan(projectId, planId);
        requireItem(planId, itemId);
        return proposalRepository.findByPlanIdAndItemIdOrderByCreatedAtDesc(planId, itemId).stream()
                .map(TransformationProposalDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransformationRunDto> runs(UUID projectId, UUID planId, UUID itemId) {
        requireProject(projectId);
        requirePlan(projectId, planId);
        requireItem(planId, itemId);
        return runRepository.findByPlanIdAndItemIdOrderByCreatedAtDesc(planId, itemId).stream()
                .map(TransformationRunDto::from).toList();
    }

    public TransformationRunDto apply(UUID projectId, UUID proposalId, boolean confirmed) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        ReentrantLock lock = locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        lock.lock();
        ApplyAttempt attempt = new ApplyAttempt();
        try {
            return transactionTemplate.execute(status -> doApply(projectId, proposalId, attempt));
        } catch (RuntimeException exception) {
            restoreQuietly(attempt);
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    private TransformationRunDto doApply(UUID projectId, UUID proposalId, ApplyAttempt attempt) {
        TransformationProposal proposal = proposalRepository.findByIdAndProjectId(proposalId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TRANSFORMATION_NOT_FOUND", "error.transformation.notFound"));
        MigrationPlan plan = requirePlan(projectId, proposal.getPlanId());
        MigrationItem item = requireItem(plan.getId(), proposal.getItemId());
        if (!item.isIncluded()) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_ITEM_EXCLUDED",
                    "error.transformation.itemExcluded");
        }
        if (proposal.getStatus() == TransformationProposalStatus.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_ALREADY_APPLIED",
                    "error.transformation.alreadyApplied");
        }
        if (proposal.getStatus() != TransformationProposalStatus.PROPOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_SUPERSEDED",
                    "error.transformation.superseded");
        }
        requirePlanCurrent(plan);
        AnalysisProject project = requireProject(projectId);
        ProjectFile file = fileRepository.findById(proposal.getSourceFileId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TRANSFORMATION_NOT_FOUND", "error.transformation.notFound"));
        Path root = projectRoot(project);
        Path target = safeSource(root, proposal.getRelativePath());
        byte[] currentBytes = readBytes(target);
        if (!sha256(currentBytes).equals(proposal.getOriginalSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        TextDecoder.DecodedSource decoded;
        try {
            decoded = decoder.decode(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        TransformationOutcome outcome;
        try {
            outcome = engine.propose(item, file, decoded.text(), file.getRelativePath());
        } catch (TransformationUnsupportedException | TransformationInvalidException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        if (!outcome.ruleId().equals(proposal.getRuleId())
                || !outcome.ruleVersion().equals(proposal.getRuleVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        byte[] proposedBytes = outcome.proposedText().getBytes(decoded.charset());
        if (!sha256(proposedBytes).equals(proposal.getProposedSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        Path backupRoot = backupRoot(root);
        UUID runId = UUID.randomUUID();
        Path backup = backupRoot.resolve(projectId.toString()).resolve(runId.toString())
                .resolve(proposal.getRelativePath()).normalize();
        if (!backup.startsWith(backupRoot) || backup.startsWith(root)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_BACKUP_UNSAFE", "error.transformation.backupUnsafe");
        }
        try {
            Files.createDirectories(backup.getParent());
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            attempt.target = target;
            attempt.originalBytes = currentBytes;
            writeAtomically(target, proposedBytes);
            attempt.written = true;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_APPLY_FAILED", "error.transformation.applyFailed");
        }
        proposal.markApplied();
        proposalRepository.save(proposal);
        TransformationRun run = TransformationRun.applied(projectId, plan.getId(),
                proposal.getId(), item.getId(), proposal.getRelativePath(), backup.toString(),
                proposal.getOriginalSha256(), proposal.getProposedSha256(),
                proposal.getRuleId(), proposal.getRuleVersion());
        run = runRepository.save(run);
        auditService.record(projectId, "TRANSFORMATION_APPLIED", "TRANSFORMATION_RUN",
                run.getId().toString(), proposal.getRelativePath());
        return TransformationRunDto.from(run);
    }

    public TransformationRunDto restore(UUID projectId, UUID runId, boolean confirmed) {
        if (!confirmed) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "TRANSFORMATION_CONFIRMATION_REQUIRED",
                    "error.transformation.confirmationRequired");
        }
        ReentrantLock lock = locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        lock.lock();
        RestoreAttempt attempt = new RestoreAttempt();
        try {
            return transactionTemplate.execute(status -> doRestore(projectId, runId, attempt));
        } catch (RuntimeException exception) {
            restoreQuietly(attempt);
            throw exception;
        } finally {
            lock.unlock();
        }
    }

    private TransformationRunDto doRestore(UUID projectId, UUID runId, RestoreAttempt attempt) {
        TransformationRun run = runRepository.findByIdAndProjectId(runId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "TRANSFORMATION_RUN_NOT_FOUND", "error.transformation.runNotFound"));
        if (run.getStatus() != TransformationRunStatus.APPLIED) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_SUPERSEDED",
                    "error.transformation.superseded");
        }
        AnalysisProject project = requireProject(projectId);
        Path root = projectRoot(project);
        Path target = safeSource(root, run.getRelativePath());
        byte[] currentBytes = readBytes(target);
        if (!sha256(currentBytes).equals(run.getAppliedSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        Path backupRoot = backupRoot(root);
        Path backup = Path.of(run.getBackupPath()).toAbsolutePath().normalize();
        if (!backup.startsWith(backupRoot) || !Files.isRegularFile(backup)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_BACKUP_UNSAFE", "error.transformation.backupUnsafe");
        }
        byte[] backupBytes = readBytes(backup);
        if (!sha256(backupBytes).equals(run.getOriginalSha256())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_BACKUP_UNSAFE", "error.transformation.backupUnsafe");
        }
        try {
            attempt.target = target;
            attempt.currentBytes = currentBytes;
            writeAtomically(target, backupBytes);
            attempt.written = true;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_APPLY_FAILED", "error.transformation.applyFailed");
        }
        run.markRestored();
        runRepository.save(run);
        proposalRepository.findById(run.getProposalId()).ifPresent(proposal -> {
            proposal.markRestored();
            proposalRepository.save(proposal);
        });
        auditService.record(projectId, "TRANSFORMATION_RESTORED", "TRANSFORMATION_RUN",
                run.getId().toString(), run.getRelativePath());
        return TransformationRunDto.from(run);
    }

    private void restoreQuietly(ApplyAttempt attempt) {
        if (attempt.written && attempt.target != null && attempt.originalBytes != null) {
            try {
                writeAtomically(attempt.target, attempt.originalBytes);
            } catch (IOException ignored) {
            }
        }
    }

    private void restoreQuietly(RestoreAttempt attempt) {
        if (attempt.written && attempt.target != null && attempt.currentBytes != null) {
            try {
                writeAtomically(attempt.target, attempt.currentBytes);
            } catch (IOException ignored) {
            }
        }
    }

    private AnalysisProject requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
    }

    private MigrationPlan requirePlan(UUID projectId, UUID planId) {
        requireProject(projectId);
        return planRepository.findByIdAndProjectId(planId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "MIGRATION_PLAN_NOT_FOUND", "error.migration.planNotFound"));
    }

    private MigrationItem requireItem(UUID planId, UUID itemId) {
        return itemRepository.findByIdAndPlanId(itemId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "MIGRATION_ITEM_NOT_FOUND", "error.migration.itemNotFound"));
    }

    private void requirePlanCurrent(MigrationPlan plan) {
        ScanRun latest = scanRepository
                .findFirstByProjectIdAndStatusOrderBySequenceDesc(plan.getProjectId(),
                        ScanStatus.COMPLETED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_PLAN_STALE", "error.transformation.planStale"));
        if (!latest.getId().equals(plan.getScanId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "TRANSFORMATION_PLAN_STALE", "error.transformation.planStale");
        }
    }

    private Path projectRoot(AnalysisProject project) {
        try {
            return Path.of(project.getSourcePath()).toRealPath();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_SOURCE_UNAVAILABLE",
                    "error.scan.sourceUnavailable");
        }
    }

    private Path safeSource(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        try {
            if (!target.startsWith(root) || Files.isSymbolicLink(target)
                    || !Files.isRegularFile(target)) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                        "error.transformation.sourceChanged");
            }
            Path real = target.toRealPath();
            if (!real.startsWith(root)) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                        "error.transformation.sourceChanged");
            }
            return real;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
    }

    private Source readSource(Path target, ProjectFile file) {
        try {
            byte[] bytes = Files.readAllBytes(target);
            String currentSha = sha256(bytes);
            if (file.getSha256() != null && !file.getSha256().equals(currentSha)) {
                throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                        "error.transformation.sourceChanged");
            }
            TextDecoder.DecodedSource decoded = decoder.decode(target);
            return new Source(decoded.text(), decoded.charset(), currentSha);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
    }

    private byte[] readBytes(Path target) {
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
    }

    private Path backupRoot(Path projectRoot) {
        String configured = properties.getTransformation().getBackupDirectory();
        Path root = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".stravique", "transformation-backups")
                : Path.of(configured).toAbsolutePath().normalize();
        if (root.toAbsolutePath().normalize().startsWith(projectRoot)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_BACKUP_UNSAFE", "error.transformation.backupUnsafe");
        }
        return root.toAbsolutePath().normalize();
    }

    private void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".stravique-transform-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "error.internal");
        }
    }

    private record Source(String text, Charset charset, String sha256) {
    }

    private static final class ApplyAttempt {
        private Path target;
        private byte[] originalBytes;
        private boolean written;
    }

    private static final class RestoreAttempt {
        private Path target;
        private byte[] currentBytes;
        private boolean written;
    }

    private static final class ProposalFactory {
        private ProposalFactory() {
        }

        private static TransformationProposal create(UUID projectId, UUID planId, UUID itemId,
                                                      UUID fileId, String relativePath,
                                                      TransformationOutcome outcome,
                                                      String originalSha, String proposedSha) {
            return TransformationProposal.create(projectId, planId, itemId, fileId, relativePath,
                    outcome.ruleId(), outcome.ruleVersion(), originalSha, proposedSha,
                    outcome.diff(), outcome.validationStatus(), outcome.validationMessage());
        }
    }
}
