package dev.stravique.infrastructure;

import dev.stravique.domain.TechnologyUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TechnologyUsageRepository extends JpaRepository<TechnologyUsage, UUID> {

    List<TechnologyUsage> findByScanIdOrderByCategoryAscNameAsc(UUID scanId);

    Optional<TechnologyUsage> findByScanIdAndTechnologyKey(UUID scanId, String technologyKey);

    long countByScanId(UUID scanId);
}
