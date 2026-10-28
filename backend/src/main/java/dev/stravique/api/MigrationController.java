package dev.stravique.api;

import dev.stravique.api.dto.MigrationGraphDto;
import dev.stravique.api.dto.MigrationItemDetailDto;
import dev.stravique.api.dto.MigrationItemDto;
import dev.stravique.api.dto.MigrationPlanDto;
import dev.stravique.api.dto.MigrationPlanRequest;
import dev.stravique.api.dto.MigrationPlanSummaryDto;
import dev.stravique.api.dto.MigrationScopeRequest;
import dev.stravique.api.dto.PageResponse;
import dev.stravique.application.MigrationPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/migration-plans")
public class MigrationController {

    private final MigrationPlanService planService;

    public MigrationController(MigrationPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public List<MigrationPlanSummaryDto> plans(@PathVariable UUID projectId) {
        return planService.plans(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MigrationPlanDto generate(
            @PathVariable UUID projectId,
            @RequestBody(required = false) MigrationPlanRequest request) {
        return planService.generate(projectId, request);
    }

    @GetMapping("/{planId}")
    public MigrationPlanDto plan(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return planService.plan(projectId, planId);
    }

    @GetMapping("/{planId}/items")
    public PageResponse<MigrationItemDto> items(
            @PathVariable UUID projectId,
            @PathVariable UUID planId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Integer phase,
            @RequestParam(required = false) Boolean attention,
            @RequestParam(required = false) Boolean included,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return planService.items(projectId, planId, status, itemType, phase, attention, included,
                q, page, size);
    }

    @GetMapping("/{planId}/items/{itemId}")
    public MigrationItemDetailDto item(@PathVariable UUID projectId, @PathVariable UUID planId,
                                       @PathVariable UUID itemId) {
        return planService.item(projectId, planId, itemId);
    }

    @PatchMapping("/{planId}/items/{itemId}")
    public MigrationItemDto updateScope(@PathVariable UUID projectId, @PathVariable UUID planId,
                                        @PathVariable UUID itemId,
                                        @Valid @RequestBody MigrationScopeRequest request) {
        return planService.setIncluded(projectId, planId, itemId, request.included());
    }

    @GetMapping("/{planId}/graph")
    public MigrationGraphDto graph(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return planService.graph(projectId, planId);
    }
}
