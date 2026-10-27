package dev.stravique.infrastructure;

import dev.stravique.domain.AnalysisProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisProjectRepository extends JpaRepository<AnalysisProject, UUID> {

    List<AnalysisProject> findAllByOrderByUpdatedAtDesc();

    boolean existsBySourcePath(String sourcePath);
}
