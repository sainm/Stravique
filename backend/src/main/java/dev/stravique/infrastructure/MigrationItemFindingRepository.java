package dev.stravique.infrastructure;

import dev.stravique.domain.MigrationItemFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MigrationItemFindingRepository extends JpaRepository<MigrationItemFinding, UUID> {

    List<MigrationItemFinding> findByItemIdOrderByRoleAsc(UUID itemId);
}
