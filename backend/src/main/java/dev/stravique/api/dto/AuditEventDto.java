package dev.stravique.api.dto;

import dev.stravique.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        String action,
        String entityType,
        String entityId,
        String detail,
        Instant occurredAt
) {
    public static AuditEventDto from(AuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetail(),
                event.getOccurredAt()
        );
    }
}
