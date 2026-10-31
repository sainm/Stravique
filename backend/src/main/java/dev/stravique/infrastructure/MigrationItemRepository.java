package dev.stravique.infrastructure;

import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MigrationItemRepository extends JpaRepository<MigrationItem, UUID>,
        JpaSpecificationExecutor<MigrationItem> {

    List<MigrationItem> findByPlanIdOrderByPhaseAscPriorityAscTitleAsc(UUID planId);

    List<MigrationItem> findByPlanIdOrderByPhaseAscPriorityAscTitleAscIdAsc(UUID planId);

    List<MigrationItem> findByPlanIdAndIncludedTrueOrderByPhaseAscPriorityAscTitleAscIdAsc(
            UUID planId);

    List<MigrationItem> findByPlanIdAndStatusInOrderByPhaseAscPriorityAscTitleAsc(
            UUID planId, List<MigrationItemStatus> statuses);

    Optional<MigrationItem> findByIdAndPlanId(UUID id, UUID planId);

    long countByPlanIdAndIncludedTrue(UUID planId);
}
