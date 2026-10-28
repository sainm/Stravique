package dev.stravique.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "migration_item_finding")
public class MigrationItemFinding {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MigrationEvidenceRole role;

    public static MigrationItemFinding create(UUID itemId, UUID findingId,
                                              MigrationEvidenceRole role) {
        MigrationItemFinding link = new MigrationItemFinding();
        link.id = UUID.randomUUID();
        link.itemId = itemId;
        link.findingId = findingId;
        link.role = role;
        return link;
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public MigrationEvidenceRole getRole() {
        return role;
    }
}
