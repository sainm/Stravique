package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.api.dto.AuditEventDto;
import dev.stravique.api.dto.PageResponse;
import dev.stravique.domain.AuditEvent;
import dev.stravique.infrastructure.AuditEventRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final AnalysisProjectRepository projectRepository;

    public AuditService(AuditEventRepository repository, AnalysisProjectRepository projectRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void record(UUID projectId, String action, String entityType, String entityId, String detail) {
        repository.save(AuditEvent.create(projectId, action, entityType, entityId, detail));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> forProject(UUID projectId, int page, int size) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                    "error.project.notFound");
        }
        return PageResponse.from(repository.findByProjectIdOrderByOccurredAtDesc(
                projectId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)))
        ), AuditEventDto::from);
    }
}
