package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.ScanDto;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ScanCoordinator {

    private static final List<ScanStatus> ACTIVE_STATUSES = List.of(
            ScanStatus.QUEUED, ScanStatus.RUNNING, ScanStatus.CANCEL_REQUESTED
    );
    private static final List<ScanStatus> TERMINAL_STATUSES = List.of(
            ScanStatus.COMPLETED, ScanStatus.FAILED, ScanStatus.CANCELLED
    );

    private final ScanRunRepository scanRepository;
    private final AnalysisProjectRepository projectRepository;
    private final ScanWorker scanWorker;
    private final AuditService auditService;
    private final ExecutorService scanExecutor;
    private final StraviqueProperties properties;
    private final Map<UUID, AtomicBoolean> controls = new ConcurrentHashMap<>();

    public ScanCoordinator(ScanRunRepository scanRepository, AnalysisProjectRepository projectRepository,
                           ScanWorker scanWorker, AuditService auditService, ExecutorService scanExecutor,
                           StraviqueProperties properties) {
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.scanWorker = scanWorker;
        this.auditService = auditService;
        this.scanExecutor = scanExecutor;
        this.properties = properties;
    }

    public synchronized ScanDto start(UUID projectId) {
        AnalysisProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
        if (scanRepository.existsByProjectIdAndStatusIn(projectId, ACTIVE_STATUSES)) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_ALREADY_ACTIVE",
                    "error.project.concurrentScan");
        }
        int sequence = scanRepository.findFirstByProjectIdOrderBySequenceDesc(project.getId())
                .map(scan -> scan.getSequence() + 1).orElse(1);
        ScanRun scan = scanRepository.save(ScanRun.queued(projectId, sequence,
                properties.getAnalyzerVersion()));
        auditService.record("SCAN_QUEUED", "SCAN", scan.getId().toString(),
                Integer.toString(scan.getSequence()));
        AtomicBoolean cancellation = new AtomicBoolean(false);
        controls.put(scan.getId(), cancellation);
        scanExecutor.execute(() -> scanWorker.execute(scan.getId(), cancellation,
                () -> controls.remove(scan.getId())));
        return ScanDto.from(scan);
    }

    public List<ScanDto> history(UUID projectId) {
        requireProject(projectId);
        return scanRepository.findByProjectIdOrderBySequenceDesc(projectId).stream()
                .map(ScanDto::from).toList();
    }

    public ScanDto get(UUID scanId) {
        return ScanDto.from(requireScan(scanId));
    }

    public synchronized ScanDto cancel(UUID scanId) {
        ScanRun scan = requireScan(scanId);
        if (TERMINAL_STATUSES.contains(scan.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_NOT_CANCELABLE",
                    "error.scan.notCancelable");
        }
        scan.requestCancel();
        scan = scanRepository.save(scan);
        auditService.record("SCAN_CANCEL_REQUESTED", "SCAN", scanId.toString(), null);
        AtomicBoolean control = controls.get(scanId);
        if (control != null) {
            control.set(true);
        } else if (scan.getStatus() == ScanStatus.CANCEL_REQUESTED) {
            scan.cancel();
            scan = scanRepository.save(scan);
            auditService.record("SCAN_CANCELLED", "SCAN", scanId.toString(), "not-running");
        }
        return ScanDto.from(scan);
    }

    public boolean hasActiveScan(UUID projectId) {
        return scanRepository.existsByProjectIdAndStatusIn(projectId, ACTIVE_STATUSES);
    }

    private ScanRun requireScan(UUID scanId) {
        return scanRepository.findById(scanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                        "error.scan.notFound"));
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "error.project.notFound");
        }
    }
}
