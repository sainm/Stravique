package dev.stravique.api;

import dev.stravique.api.dto.TransformationConfirmRequest;
import dev.stravique.api.dto.TransformationCoverageDto;
import dev.stravique.api.dto.TransformationPlanRunDto;
import dev.stravique.api.dto.TransformationPlanRunStartRequest;
import dev.stravique.api.dto.TransformationPlanRunSummaryDto;
import dev.stravique.application.TransformationPlanRunService;
import dev.stravique.config.RequestLocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/projects/{projectId}/migration-plans/{planId}")
public class TransformationPlanRunController {

    private final TransformationPlanRunService planRunService;
    private final RequestLocaleSupport localeSupport;

    public TransformationPlanRunController(TransformationPlanRunService planRunService,
                                           RequestLocaleSupport localeSupport) {
        this.planRunService = planRunService;
        this.localeSupport = localeSupport;
    }

    @GetMapping("/transformation-coverage")
    public TransformationCoverageDto coverage(@PathVariable UUID projectId,
                                              @PathVariable UUID planId) {
        return planRunService.coverage(projectId, planId);
    }

    @GetMapping("/transformation-plan-runs")
    public List<TransformationPlanRunSummaryDto> history(@PathVariable UUID projectId,
                                                         @PathVariable UUID planId) {
        return planRunService.history(projectId, planId);
    }

    @GetMapping("/transformation-plan-runs/{planRunId}")
    public TransformationPlanRunDto detail(@PathVariable UUID projectId,
                                           @PathVariable UUID planId,
                                           @PathVariable UUID planRunId) {
        return planRunService.detail(projectId, planId, planRunId);
    }

    @PostMapping("/transformation-plan-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransformationPlanRunDto apply(@PathVariable UUID projectId,
                                          @PathVariable UUID planId,
                                          @Valid @RequestBody TransformationPlanRunStartRequest request,
                                          HttpServletRequest httpRequest) {
        return planRunService.apply(projectId, planId, request.confirmed(), request.itemIds(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/transformation-plan-runs/{planRunId}/cancel")
    public TransformationPlanRunDto cancel(@PathVariable UUID projectId,
                                           @PathVariable UUID planId,
                                           @PathVariable UUID planRunId,
                                           @Valid @RequestBody TransformationConfirmRequest request,
                                           HttpServletRequest httpRequest) {
        return planRunService.cancel(projectId, planId, planRunId, request.confirmed(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/transformation-plan-runs/{planRunId}/restore")
    public TransformationPlanRunDto restore(@PathVariable UUID projectId,
                                            @PathVariable UUID planId,
                                            @PathVariable UUID planRunId,
                                            @Valid @RequestBody TransformationConfirmRequest request,
                                            HttpServletRequest httpRequest) {
        return planRunService.restore(projectId, planId, planRunId, request.confirmed(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/transformation-plan-runs/{planRunId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransformationPlanRunDto resume(@PathVariable UUID projectId,
                                           @PathVariable UUID planId,
                                           @PathVariable UUID planRunId,
                                           @Valid @RequestBody TransformationConfirmRequest request,
                                           HttpServletRequest httpRequest) {
        return planRunService.resume(projectId, planId, planRunId, request.confirmed(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/transformation-plan-runs/{planRunId}/recover")
    public TransformationPlanRunDto recover(@PathVariable UUID projectId,
                                            @PathVariable UUID planId,
                                            @PathVariable UUID planRunId,
                                            @Valid @RequestBody TransformationConfirmRequest request,
                                            HttpServletRequest httpRequest) {
        return planRunService.recover(projectId, planId, planRunId, request.confirmed(),
                localeSupport.resolve(httpRequest));
    }
}
