package dev.stravique.api;

import dev.stravique.api.dto.TransformationBatchApplyRequest;
import dev.stravique.api.dto.TransformationBatchDto;
import dev.stravique.api.dto.TransformationBatchPreviewRequest;
import dev.stravique.api.dto.TransformationBatchRestoreRequest;
import dev.stravique.application.TransformationBatchService;
import dev.stravique.config.RequestLocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class TransformationBatchController {

    private final TransformationBatchService batchService;
    private final RequestLocaleSupport localeSupport;

    public TransformationBatchController(TransformationBatchService batchService,
                                         RequestLocaleSupport localeSupport) {
        this.batchService = batchService;
        this.localeSupport = localeSupport;
    }

    @PostMapping("/migration-plans/{planId}/transformation-batches/preview")
    public TransformationBatchDto preview(@PathVariable UUID projectId,
                                          @PathVariable UUID planId,
                                          @Valid @RequestBody TransformationBatchPreviewRequest request,
                                          HttpServletRequest httpRequest) {
        return batchService.preview(projectId, planId, request.itemIds(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/migration-plans/{planId}/transformation-batches/apply")
    public TransformationBatchDto apply(@PathVariable UUID projectId,
                                        @PathVariable UUID planId,
                                        @Valid @RequestBody TransformationBatchApplyRequest request,
                                        HttpServletRequest httpRequest) {
        return batchService.apply(projectId, planId, request.itemIds(), request.confirmed(),
                localeSupport.resolve(httpRequest));
    }

    @PostMapping("/migration-plans/{planId}/transformation-batches/restore")
    public TransformationBatchDto restore(@PathVariable UUID projectId,
                                          @PathVariable UUID planId,
                                          @Valid @RequestBody TransformationBatchRestoreRequest request,
                                          HttpServletRequest httpRequest) {
        return batchService.restore(projectId, planId, request.runIds(), request.confirmed(),
                localeSupport.resolve(httpRequest));
    }
}
