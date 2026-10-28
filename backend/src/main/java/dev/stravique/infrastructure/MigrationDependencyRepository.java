package dev.stravique.infrastructure;

import dev.stravique.domain.MigrationDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MigrationDependencyRepository extends JpaRepository<MigrationDependency, UUID> {

    List<MigrationDependency> findByPlanIdOrderByEvidencePathAscLineStartAsc(UUID planId);

    List<MigrationDependency> findByPlanIdAndFromItemIdOrderByEvidencePathAscLineStartAsc(
            UUID planId, UUID fromItemId);

    List<MigrationDependency> findByPlanIdAndToItemIdOrderByEvidencePathAscLineStartAsc(
            UUID planId, UUID toItemId);

    long countByPlanId(UUID planId);
}
