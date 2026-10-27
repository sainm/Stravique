package dev.stravique.infrastructure;

import dev.stravique.domain.FileScanStatus;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ProjectFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, UUID>,
        JpaSpecificationExecutor<ProjectFile> {

    List<ProjectFile> findByScanIdOrderByRelativePathAsc(UUID scanId);

    Optional<ProjectFile> findByScanIdAndRelativePath(UUID scanId, String relativePath);

    Page<ProjectFile> findByScanId(UUID scanId, Pageable pageable);

    Page<ProjectFile> findByScanIdAndFileType(UUID scanId, FileType fileType, Pageable pageable);

    Page<ProjectFile> findByScanIdAndScanStatus(UUID scanId, FileScanStatus scanStatus, Pageable pageable);

    long countByScanIdAndScanStatus(UUID scanId, FileScanStatus scanStatus);
}
