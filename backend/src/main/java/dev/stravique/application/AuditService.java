package dev.stravique.application;

import dev.stravique.domain.AuditEvent;
import dev.stravique.infrastructure.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String detail) {
        repository.save(AuditEvent.create(action, entityType, entityId, detail));
    }
}
