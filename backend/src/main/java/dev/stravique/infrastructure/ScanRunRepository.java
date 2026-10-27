package dev.stravique.infrastructure;

import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
