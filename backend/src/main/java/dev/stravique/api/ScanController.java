package dev.stravique.api;

import dev.stravique.api.dto.ScanDto;
import dev.stravique.application.ScanCoordinator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ScanController {

    private final ScanCoordinator coordinator;

    public ScanController(ScanCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/projects/{projectId}/scans")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ScanDto start(@PathVariable UUID projectId) {
        return coordinator.start(projectId);
    }

    @GetMapping("/projects/{projectId}/scans")
    public List<ScanDto> history(@PathVariable UUID projectId) {
        return coordinator.history(projectId);
    }

    @GetMapping("/scans/{scanId}")
    public ScanDto get(@PathVariable UUID scanId) {
        return coordinator.get(scanId);
    }

    @PostMapping("/scans/{scanId}/cancel")
    public ScanDto cancel(@PathVariable UUID scanId) {
        return coordinator.cancel(scanId);
    }
}
