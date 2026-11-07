package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.ScanComparisonDto;
import dev.stravique.api.dto.ScanComparisonFileDto;
import dev.stravique.api.dto.ScanComparisonRowDto;
import dev.stravique.api.dto.ScanComparisonSummaryDto;
import dev.stravique.api.dto.ScanDto;
import dev.stravique.domain.FileChangeType;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ScanComparisonService {

    static final int MAX_ROWS = 2000;

    private final AnalysisProjectRepository projectRepository;
    private final ScanRunRepository scanRepository;
    private final ProjectFileRepository fileRepository;

    public ScanComparisonService(AnalysisProjectRepository projectRepository,
                                 ScanRunRepository scanRepository,
                                 ProjectFileRepository fileRepository) {
        this.projectRepository = projectRepository;
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public ScanComparisonDto compare(UUID projectId, UUID scanId, UUID baseScanId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                    "error.project.notFound");
        }
        ScanRun current = requireScan(projectId, scanId);
        requireCompleted(current);
        ScanRun base = resolveBase(projectId, current, baseScanId);
        ScanComparison.Result result = ScanComparison.compare(
                fileViews(current.getId()), fileViews(base.getId()));
        List<ScanComparisonRowDto> rows = result.rows().stream()
                .limit(MAX_ROWS)
                .map(row -> new ScanComparisonRowDto(
                        row.status(),
                        row.relativePath(),
                        ScanComparisonFileDto.from(row.before()),
                        ScanComparisonFileDto.from(row.after())))
                .toList();
        return new ScanComparisonDto(
                projectId,
                ScanDto.from(current),
                ScanDto.from(base),
                new ScanComparisonSummaryDto(result.added(), result.removed(), result.changed(),
                        result.unchanged(), result.unknown()),
                rows,
                result.rows().size() > MAX_ROWS,
                MAX_ROWS
        );
    }

    private ScanRun resolveBase(UUID projectId, ScanRun current, UUID baseScanId) {
        if (baseScanId != null) {
            if (baseScanId.equals(current.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "SCAN_COMPARISON_SELF",
                        "error.scan.comparisonSelf");
            }
            ScanRun base = requireScan(projectId, baseScanId);
            requireCompleted(base);
            return base;
        }
        return scanRepository
                .findFirstByProjectIdAndStatusAndSequenceLessThanOrderBySequenceDesc(
                        projectId, ScanStatus.COMPLETED, current.getSequence())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "SCAN_COMPARISON_UNAVAILABLE", "error.scan.comparisonUnavailable"));
    }

    private void requireCompleted(ScanRun scan) {
        if (scan.getStatus() != ScanStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "SCAN_COMPARISON_UNAVAILABLE",
                    "error.scan.comparisonUnavailable");
        }
    }

    private ScanRun requireScan(UUID projectId, UUID scanId) {
        return scanRepository.findByIdAndProjectId(scanId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                        "error.scan.notFound"));
    }

    private List<ScanComparison.FileView> fileViews(UUID scanId) {
        return fileRepository.findByScanIdOrderByRelativePathAsc(scanId).stream()
                .filter(file -> file.getChangeType() != FileChangeType.DELETED)
                .map(file -> new ScanComparison.FileView(
                        file.getRelativePath(),
                        file.getFileType().name(),
                        file.getSizeBytes(),
                        file.getSha256(),
                        file.getEncoding(),
                        file.getScanStatus().name(),
                        file.getParseStatus().name(),
                        file.getStructureRole(),
                        file.isGenerated()))
                .toList();
    }
}
