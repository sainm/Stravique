package dev.stravique.infrastructure;

import dev.stravique.domain.ModuleDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModuleDependencyRepository extends JpaRepository<ModuleDependency, UUID> {

    List<ModuleDependency> findByScanIdOrderByEvidencePathAscLineStartAsc(UUID scanId);
}
