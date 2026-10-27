package dev.stravique.infrastructure;

import dev.stravique.domain.PathOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PathOverrideRepository extends JpaRepository<PathOverride, UUID> {

    List<PathOverride> findAllByProjectIdOrderByRelativePathAsc(UUID projectId);

    void deleteAllByProjectId(UUID projectId);
}
