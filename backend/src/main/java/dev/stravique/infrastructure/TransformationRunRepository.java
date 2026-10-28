package dev.stravique.infrastructure;

import dev.stravique.domain.TransformationRun;
import dev.stravique.domain.TransformationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransformationRunRepository extends JpaRepository<TransformationRun, UUID> {

    List<TransformationRun> findByPlanIdAndItemIdOrderByCreatedAtDesc(UUID planId, UUID itemId);

    Optional<TransformationRun> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<TransformationRun> findFirstByProposalIdAndStatusOrderByCreatedAtDesc(
            UUID proposalId, TransformationRunStatus status);
}
