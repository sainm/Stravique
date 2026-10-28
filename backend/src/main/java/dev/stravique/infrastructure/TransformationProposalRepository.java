package dev.stravique.infrastructure;

import dev.stravique.domain.TransformationProposal;
import dev.stravique.domain.TransformationProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransformationProposalRepository extends JpaRepository<TransformationProposal, UUID> {

    List<TransformationProposal> findByPlanIdAndItemIdOrderByCreatedAtDesc(UUID planId, UUID itemId);

    List<TransformationProposal> findByPlanIdAndItemIdAndStatus(
            UUID planId, UUID itemId, TransformationProposalStatus status);

    Optional<TransformationProposal> findByIdAndProjectId(UUID id, UUID projectId);
}
