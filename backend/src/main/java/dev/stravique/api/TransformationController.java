package dev.stravique.api;

import dev.stravique.api.dto.TransformationConfirmRequest;
import dev.stravique.api.dto.TransformationProposalDto;
import dev.stravique.api.dto.TransformationRunDto;
import dev.stravique.application.TransformationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class TransformationController {

    private final TransformationService transformationService;

    public TransformationController(TransformationService transformationService) {
        this.transformationService = transformationService;
    }

    @GetMapping("/migration-plans/{planId}/items/{itemId}/transformation-proposals")
    public List<TransformationProposalDto> proposals(@PathVariable UUID projectId,
                                                     @PathVariable UUID planId,
                                                     @PathVariable UUID itemId) {
        return transformationService.proposals(projectId, planId, itemId);
    }

    @PostMapping("/migration-plans/{planId}/items/{itemId}/transformation-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public TransformationProposalDto propose(@PathVariable UUID projectId,
                                             @PathVariable UUID planId,
                                             @PathVariable UUID itemId) {
        return transformationService.propose(projectId, planId, itemId);
    }

    @GetMapping("/migration-plans/{planId}/items/{itemId}/transformation-runs")
    public List<TransformationRunDto> runs(@PathVariable UUID projectId,
                                           @PathVariable UUID planId,
                                           @PathVariable UUID itemId) {
        return transformationService.runs(projectId, planId, itemId);
    }

    @PostMapping("/transformation-proposals/{proposalId}/apply")
    public TransformationRunDto apply(@PathVariable UUID projectId,
                                      @PathVariable UUID proposalId,
                                      @Valid @RequestBody TransformationConfirmRequest request) {
        return transformationService.apply(projectId, proposalId, request.confirmed());
    }

    @PostMapping("/transformation-runs/{runId}/restore")
    public TransformationRunDto restore(@PathVariable UUID projectId,
                                        @PathVariable UUID runId,
                                        @Valid @RequestBody TransformationConfirmRequest request) {
        return transformationService.restore(projectId, runId, request.confirmed());
    }
}
