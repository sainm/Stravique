package dev.stravique.infrastructure;

import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanRunRepository extends JpaRepository<ScanRun, UUID> {

    List<ScanRun> findByProjectIdOrderBySequenceDesc(UUID projectId);

    Optional<ScanRun> findFirstByProjectIdOrderBySequenceDesc(UUID projectId);

    Optional<ScanRun> findFirstByProjectIdAndStatusOrderBySequenceDesc(UUID projectId, ScanStatus status);

    boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<ScanStatus> statuses);

    Optional<ScanRun> findFirstByProjectIdAndStatusInOrderBySequenceDesc(UUID projectId,
                                                                       Collection<ScanStatus> statuses);

    List<ScanRun> findByStatusIn(Collection<ScanStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ScanRun scan set scan.status = :target where scan.id = :id and scan.status in :allowed")
    int transitionStatus(@Param("id") UUID id, @Param("target") ScanStatus target,
                         @Param("allowed") Collection<ScanStatus> allowed);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ScanRun scan set scan.status = :target, scan.finishedAt = :finishedAt "
            + "where scan.id = :id and scan.status = :current")
    int finishFrom(@Param("id") UUID id, @Param("current") ScanStatus current,
                   @Param("target") ScanStatus target,
                   @Param("finishedAt") java.time.Instant finishedAt);
}
