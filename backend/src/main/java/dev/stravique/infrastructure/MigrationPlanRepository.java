package dev.stravique.infrastructure;

import dev.stravique.domain.MigrationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationPlanRepository extends JpaRepository<MigrationPlan, UUID> {

    List<MigrationPlan> findTop20ByProjectIdOrderBySequenceDesc(UUID projectId);

    Optional<MigrationPlan> findFirstByProjectIdOrderBySequenceDesc(UUID projectId);

    Optional<MigrationPlan> findByIdAndProjectId(UUID id, UUID projectId);
}
