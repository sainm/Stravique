package dev.stravique.application;

import dev.stravique.api.dto.ScanComparisonDto;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanComparisonServiceTest {

    private AnalysisProjectRepository projectRepository;
    private ScanRunRepository scanRepository;
    private ProjectFileRepository fileRepository;
    private ScanComparisonService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(AnalysisProjectRepository.class);
        scanRepository = mock(ScanRunRepository.class);
        fileRepository = mock(ProjectFileRepository.class);
        service = new ScanComparisonService(projectRepository, scanRepository, fileRepository);
    }

    @Test
    void capsRowsWhileKeepingTheTrueSummaryCounts() {
        UUID projectId = UUID.randomUUID();
        ScanRun base = completedScan(projectId, 1);
        ScanRun current = completedScan(projectId, 2);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(scanRepository.findByIdAndProjectId(current.getId(), projectId))
                .thenReturn(Optional.of(current));
        when(scanRepository.findByIdAndProjectId(base.getId(), projectId))
                .thenReturn(Optional.of(base));
        when(fileRepository.findByScanIdOrderByRelativePathAsc(base.getId()))
                .thenReturn(List.of());
        when(fileRepository.findByScanIdOrderByRelativePathAsc(current.getId()))
                .thenReturn(IntStream.range(0, ScanComparisonService.MAX_ROWS + 5)
                        .mapToObj(index -> file(current, index))
                        .toList());

        ScanComparisonDto result = service.compare(projectId, current.getId(), base.getId());

        assertThat(result.rows()).hasSize(ScanComparisonService.MAX_ROWS);
        assertThat(result.truncated()).isTrue();
        assertThat(result.rowLimit()).isEqualTo(ScanComparisonService.MAX_ROWS);
        assertThat(result.summary().added()).isEqualTo(ScanComparisonService.MAX_ROWS + 5);
        assertThat(result.summary().unchanged()).isZero();
    }

    @Test
    void defaultsToThePreviousCompletedScan() {
        UUID projectId = UUID.randomUUID();
        ScanRun base = completedScan(projectId, 4);
        ScanRun current = completedScan(projectId, 7);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(scanRepository.findByIdAndProjectId(current.getId(), projectId))
                .thenReturn(Optional.of(current));
        when(scanRepository
                .findFirstByProjectIdAndStatusAndSequenceLessThanOrderBySequenceDesc(
                        projectId, dev.stravique.domain.ScanStatus.COMPLETED, 7))
                .thenReturn(Optional.of(base));
        when(fileRepository.findByScanIdOrderByRelativePathAsc(base.getId()))
                .thenReturn(List.of(file(base, 1)));
        when(fileRepository.findByScanIdOrderByRelativePathAsc(current.getId()))
                .thenReturn(List.of(file(current, 1)));

        ScanComparisonDto result = service.compare(projectId, current.getId(), null);

        assertThat(result.baseScan().id()).isEqualTo(base.getId());
        assertThat(result.summary().unchanged()).isEqualTo(1);
    }

    private ScanRun completedScan(UUID projectId, int sequence) {
        ScanRun scan = ScanRun.queued(projectId, sequence, "1.0.0");
        scan.start();
        scan.complete();
        return scan;
    }

    private ProjectFile file(ScanRun scan, int index) {
        return ProjectFile.discovered(scan.getId(), scan.getProjectId(), null,
                "src/File" + index + ".java", "File" + index + ".java", FileType.JAVA,
                10, Instant.now(), "hash-" + index, false);
    }
}
