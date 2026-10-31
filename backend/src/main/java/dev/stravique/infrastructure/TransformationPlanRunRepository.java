package dev.stravique.infrastructure;

import dev.stravique.domain.TransformationPlanRun;
import dev.stravique.domain.TransformationPlanRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransformationPlanRunRepository extends JpaRepository<TransformationPlanRun, UUID> {

    List<TransformationPlanRun> findByProjectIdAndPlanIdOrderByCreatedAtDesc(
            UUID projectId, UUID planId);

    List<TransformationPlanRun> findByStatus(TransformationPlanRunStatus status);

    boolean existsByProjectIdAndPlanIdAndStatus(
            UUID projectId, UUID planId, TransformationPlanRunStatus status);

    Optional<TransformationPlanRun> findByIdAndProjectIdAndPlanId(
            UUID id, UUID projectId, UUID planId);
}
