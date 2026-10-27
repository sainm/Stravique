package dev.stravique.infrastructure;

import dev.stravique.domain.ProjectModule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectModuleRepository extends JpaRepository<ProjectModule, UUID> {

    List<ProjectModule> findByScanIdOrderByRelativePathAsc(UUID scanId);

    long countByScanId(UUID scanId);
}
