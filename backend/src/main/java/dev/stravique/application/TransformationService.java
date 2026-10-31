package dev.stravique.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.api.ApiException;
import dev.stravique.api.dto.TransformationProposalDto;
import dev.stravique.api.dto.TransformationRunDto;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationPlan;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.domain.TransformationArtifactType;
import dev.stravique.domain.TransformationProposal;
import dev.stravique.domain.TransformationProposalStatus;
import dev.stravique.domain.TransformationRun;
import dev.stravique.domain.TransformationRunStatus;
import dev.stravique.infrastructure.AnalysisFindingRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.MigrationItemRepository;
import dev.stravique.infrastructure.MigrationPlanRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.infrastructure.TransformationProposalRepository;
import dev.stravique.infrastructure.TransformationRunRepository;
import dev.stravique.inventory.TextDecoder;
import dev.stravique.transformation.ControllerRoute;
import dev.stravique.transformation.SafeFileWriter;
import dev.stravique.transformation.TransformationEngine;
import dev.stravique.transformation.TransformationRequest;
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

    private static final String STRUTS_ACTION_RULE = "STRUTS-CONFIG-ACTION-001";
    private static final String STRUTS_FORWARD_RULE = "STRUTS-CONFIG-FORWARD-001";
    private static final List<String> ROUTE_RULES = List.of(STRUTS_ACTION_RULE, STRUTS_FORWARD_RULE);

    private final AnalysisProjectRepository projectRepository;
    private final MigrationPlanRepository planRepository;
    private final MigrationItemRepository itemRepository;
    private final ProjectFileRepository fileRepository;
    private final ScanRunRepository scanRepository;
    private final AnalysisFindingRepository findingRepository;
    private final TransformationProposalRepository proposalRepository;
    private final TransformationRunRepository runRepository;
    private final TransformationEngine engine;
    private final TextDecoder decoder;
    private final ObjectMapper objectMapper;
    private final StraviqueProperties properties;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Map<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TransformationService(AnalysisProjectRepository projectRepository,
                                 MigrationPlanRepository planRepository,
                                 MigrationItemRepository itemRepository,
                                 ProjectFileRepository fileRepository,
                                 ScanRunRepository scanRepository,
                                 AnalysisFindingRepository findingRepository,
                                 TransformationProposalRepository proposalRepository,
                                 TransformationRunRepository runRepository,
                                 TransformationEngine engine,
                                 TextDecoder decoder,
                                 ObjectMapper objectMapper,
                                 StraviqueProperties properties,
                                 AuditService auditService,
                                 PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.fileRepository = fileRepository;
        this.scanRepository = scanRepository;
        this.findingRepository = findingRepository;
        this.proposalRepository = proposalRepository;
        this.runRepository = runRepository;
        this.engine = engine;
        this.decoder = decoder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T withProjectLock(UUID projectId, java.util.function.Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
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
            outcome = engine.propose(new TransformationRequest(item, file, source.text(),
                    file.getRelativePath(), routes(plan.getScanId())));
        } catch (TransformationUnsupportedException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_UNSUPPORTED", "error.transformation.unsupported");
        } catch (TransformationInvalidException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_INVALID", "error.transformation.invalid");
        }
        if (outcome.artifactType() == TransformationArtifactType.CREATE_FILE) {
            safeNewTarget(root, outcome.targetRelativePath());
        }
        byte[] proposedBytes = proposedBytes(outcome, source.charset());
        String proposedSha = sha256(proposedBytes);
        for (TransformationProposal previous : proposalRepository
                .findByPlanIdAndItemIdAndStatus(planId, itemId,
                        TransformationProposalStatus.PROPOSED)) {
            previous.supersede();
            proposalRepository.save(previous);
        }
        TransformationProposal proposal = ProposalFactory.create(projectId, planId, itemId,
                file.getId(), outcome.targetRelativePath(), outcome, source.sha256(), proposedSha);
        proposalRepository.save(proposal);
        auditService.record(projectId, "TRANSFORMATION_PROPOSED", "TRANSFORMATION_PROPOSAL",
                proposal.getId().toString(), outcome.targetRelativePath());
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
        if (proposal.getArtifactType() == TransformationArtifactType.CREATE_FILE) {
            return doApplyCreate(projectId, project, plan, item, proposal, file, attempt);
        }
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
            outcome = engine.propose(new TransformationRequest(item, file, decoded.text(),
                    file.getRelativePath(), routes(plan.getScanId())));
        } catch (TransformationUnsupportedException | TransformationInvalidException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        if (!outcome.ruleId().equals(proposal.getRuleId())
                || !outcome.ruleVersion().equals(proposal.getRuleVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        byte[] proposedBytes = proposedBytes(outcome, decoded.charset());
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
            SafeFileWriter.write(target, proposedBytes);
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
                proposal.getRuleId(), proposal.getRuleVersion(), proposal.getArtifactType());
        run = runRepository.save(run);
        auditService.record(projectId, "TRANSFORMATION_APPLIED", "TRANSFORMATION_RUN",
                run.getId().toString(), proposal.getRelativePath());
        return TransformationRunDto.from(run);
    }

    private TransformationRunDto doApplyCreate(UUID projectId, AnalysisProject project,
                                               MigrationPlan plan, MigrationItem item,
                                               TransformationProposal proposal, ProjectFile file,
                                               ApplyAttempt attempt) {
        Path root = projectRoot(project);
        Path sourceTarget = safeSource(root, file.getRelativePath());
        byte[] sourceBytes = readBytes(sourceTarget);
        if (!sha256(sourceBytes).equals(proposal.getOriginalSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        TextDecoder.DecodedSource decoded;
        try {
            decoded = decoder.decode(sourceTarget);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        TransformationOutcome outcome;
        try {
            outcome = engine.propose(new TransformationRequest(item, file, decoded.text(),
                    file.getRelativePath(), routes(plan.getScanId())));
        } catch (TransformationUnsupportedException | TransformationInvalidException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        if (!outcome.ruleId().equals(proposal.getRuleId())
                || !outcome.ruleVersion().equals(proposal.getRuleVersion())
                || outcome.artifactType() != TransformationArtifactType.CREATE_FILE
                || !outcome.targetRelativePath().equals(proposal.getRelativePath())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        byte[] proposedBytes = proposedBytes(outcome, decoded.charset());
        if (!sha256(proposedBytes).equals(proposal.getProposedSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFORMATION_RULE_CHANGED",
                    "error.transformation.ruleChanged");
        }
        Path target = safeNewTarget(root, proposal.getRelativePath());
        try {
            SafeFileWriter.write(target, proposedBytes);
            attempt.target = target;
            attempt.created = true;
            attempt.written = true;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "TRANSFORMATION_APPLY_FAILED", "error.transformation.applyFailed");
        }
        proposal.markApplied();
        proposalRepository.save(proposal);
        TransformationRun run = TransformationRun.applied(projectId, plan.getId(),
                proposal.getId(), item.getId(), proposal.getRelativePath(), null,
                proposal.getOriginalSha256(), proposal.getProposedSha256(),
                proposal.getRuleId(), proposal.getRuleVersion(), TransformationArtifactType.CREATE_FILE);
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
        if (run.getArtifactType() == TransformationArtifactType.CREATE_FILE) {
            return doRestoreCreate(projectId, run, attempt);
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
            SafeFileWriter.write(target, backupBytes);
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

    private TransformationRunDto doRestoreCreate(UUID projectId, TransformationRun run,
                                                   RestoreAttempt attempt) {
        AnalysisProject project = requireProject(projectId);
        Path root = projectRoot(project);
        Path target = safeSource(root, run.getRelativePath());
        byte[] currentBytes = readBytes(target);
        if (!sha256(currentBytes).equals(run.getAppliedSha256())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_CHANGED",
                    "error.transformation.sourceChanged");
        }
        attempt.target = target;
        attempt.currentBytes = currentBytes;
        attempt.written = true;
        try {
            Files.delete(target);
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
        if (attempt.created && attempt.target != null) {
            try {
                Files.deleteIfExists(attempt.target);
            } catch (IOException ignored) {
            }
            return;
        }
        if (attempt.written && attempt.target != null && attempt.originalBytes != null) {
            try {
                SafeFileWriter.write(attempt.target, attempt.originalBytes);
            } catch (IOException ignored) {
            }
        }
    }

    private void restoreQuietly(RestoreAttempt attempt) {
        if (attempt.written && attempt.target != null && attempt.currentBytes != null) {
            try {
                SafeFileWriter.write(attempt.target, attempt.currentBytes);
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

    private List<ControllerRoute> routes(UUID scanId) {
        List<AnalysisFinding> findings = findingRepository
                .findByScanIdAndRuleIdInOrderByFilePathAscLineStartAsc(scanId, ROUTE_RULES);
        java.util.Map<String, String> actionTypes = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> forwardPaths = new java.util.LinkedHashMap<>();
        for (AnalysisFinding finding : findings) {
            JsonNode metadata = metadata(finding);
            if (finding.getRuleId().equals(STRUTS_ACTION_RULE)) {
                String path = metadata.path("path").asText("");
                String type = metadata.path("type").asText("");
                if (!path.isBlank() && !type.isBlank()) {
                    actionTypes.putIfAbsent(path, type);
                }
            } else if (finding.getRuleId().equals(STRUTS_FORWARD_RULE)) {
                String actionPath = metadata.path("actionPath").asText("");
                String path = metadata.path("path").asText("");
                String redirect = metadata.path("redirect").asText("");
                if (!actionPath.isBlank() && !path.isBlank()
                        && !redirect.equalsIgnoreCase("true")) {
                    forwardPaths.putIfAbsent(actionPath, path);
                }
            }
        }
        return actionTypes.entrySet().stream()
                .map(entry -> new ControllerRoute(entry.getKey(), entry.getValue(),
                        forwardPaths.getOrDefault(entry.getKey(), ""), ""))
                .toList();
    }

    private JsonNode metadata(AnalysisFinding finding) {
        if (finding.getMetadata() == null || finding.getMetadata().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(finding.getMetadata());
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
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

    private Path safeNewTarget(Path root, String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        try {
            if (!target.startsWith(root) || Files.exists(target)
                    || Files.isSymbolicLink(target)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "TRANSFORMATION_TARGET_EXISTS", "error.transformation.targetExists");
            }
            Path parent = target.getParent();
            if (parent == null || !Files.isDirectory(parent)
                    || !parent.toRealPath().startsWith(root)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRANSFORMATION_TARGET_INVALID", "error.transformation.targetInvalid");
            }
            return target;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFORMATION_TARGET_INVALID", "error.transformation.targetInvalid");
        }
    }

    private Source readSource(Path target, ProjectFile file) {
        try {
            byte[] bytes = Files.readAllBytes(target);
            String currentSha = sha256(bytes);
            if (file.getSha256() != null && !file.getSha256().equals(currentSha)
                    && !isLatestAppliedContent(file, currentSha)) {
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

    private boolean isLatestAppliedContent(ProjectFile file, String currentSha) {
        return runRepository
                .findFirstByProjectIdAndRelativePathAndStatusOrderByCreatedAtDesc(
                        file.getProjectId(), file.getRelativePath(),
                        TransformationRunStatus.APPLIED)
                .map(run -> currentSha.equals(run.getAppliedSha256()))
                .orElse(false);
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

    private byte[] proposedBytes(TransformationOutcome outcome, Charset sourceCharset) {
        Charset charset = outcome.proposedEncoding() == null
                ? sourceCharset : Charset.forName(outcome.proposedEncoding());
        return outcome.proposedText().getBytes(charset);
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
        private boolean created;
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
                    outcome.diff(), outcome.validationStatus(), outcome.validationMessage(),
                    outcome.artifactType());
        }
    }
}
