package dev.stravique.api;

import dev.stravique.api.dto.TransformationBackupLedgerDto;
import dev.stravique.api.dto.TransformationBackupPruneDto;
import dev.stravique.api.dto.TransformationConfirmRequest;
import dev.stravique.application.TransformationBackupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/transformation-backups")
public class TransformationBackupController {

    private final TransformationBackupService backupService;

    public TransformationBackupController(TransformationBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public TransformationBackupLedgerDto ledger(@PathVariable UUID projectId) {
        return backupService.ledger(projectId);
    }

    @PostMapping("/prune")
    public TransformationBackupPruneDto prune(@PathVariable UUID projectId,
                                              @Valid @RequestBody TransformationConfirmRequest request) {
        return backupService.prune(projectId, request.confirmed());
    }
}
