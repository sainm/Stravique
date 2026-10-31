package dev.stravique.infrastructure;

import dev.stravique.domain.TransformationPlanRunItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransformationPlanRunItemRepository
        extends JpaRepository<TransformationPlanRunItem, UUID> {

    List<TransformationPlanRunItem> findByPlanRunIdOrderByOrdinalAsc(UUID planRunId);
}
