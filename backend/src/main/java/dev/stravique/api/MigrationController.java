package dev.stravique.api;

import dev.stravique.api.dto.MigrationGraphDto;
import dev.stravique.api.dto.MigrationItemDetailDto;
import dev.stravique.api.dto.MigrationItemDto;
import dev.stravique.api.dto.MigrationPlanDto;
import dev.stravique.api.dto.MigrationPlanComparisonDto;
import dev.stravique.api.dto.MigrationPlanRequest;
import dev.stravique.api.dto.MigrationPlanSummaryDto;
import dev.stravique.api.dto.MigrationScopeRequest;
import dev.stravique.api.dto.PageResponse;
import dev.stravique.application.MigrationPlanComparisonExportService;
import dev.stravique.application.MigrationPlanExportService;
import dev.stravique.application.MigrationPlanService;
import dev.stravique.application.ReportService;
import dev.stravique.config.RequestLocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/migration-plans")
public class MigrationController {

    private final MigrationPlanService planService;
    private final MigrationPlanComparisonExportService exportService;
    private final MigrationPlanExportService planExportService;
    private final RequestLocaleSupport localeSupport;

    public MigrationController(MigrationPlanService planService,
                               MigrationPlanComparisonExportService exportService,
                               MigrationPlanExportService planExportService,
                               RequestLocaleSupport localeSupport) {
        this.planService = planService;
        this.exportService = exportService;
        this.planExportService = planExportService;
        this.localeSupport = localeSupport;
    }

    @GetMapping
    public List<MigrationPlanSummaryDto> plans(@PathVariable UUID projectId) {
        return planService.plans(projectId);
    }

    @GetMapping("/options")
    public List<MigrationPlanSummaryDto> planOptions(@PathVariable UUID projectId) {
        return planService.planOptions(projectId);
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

    @GetMapping("/{planId}/comparison")
    public MigrationPlanComparisonDto comparison(
            @PathVariable UUID projectId,
            @PathVariable UUID planId,
            @RequestParam(required = false) UUID basePlanId) {
        return planService.comparison(projectId, planId, basePlanId);
    }

    @GetMapping("/{planId}/export")
    public ResponseEntity<byte[]> planExport(
            @PathVariable UUID projectId,
            @PathVariable UUID planId,
            @RequestParam String format,
            @RequestParam(required = false) String lang,
            HttpServletRequest request) {
        ReportService.ReportDownload download = planExportService.export(
                projectId, planId, format, localeSupport.resolve(lang, request));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @GetMapping("/{planId}/comparison/export")
    public ResponseEntity<byte[]> comparisonExport(
            @PathVariable UUID projectId,
            @PathVariable UUID planId,
            @RequestParam String format,
            @RequestParam(required = false) UUID basePlanId,
            @RequestParam(required = false) String lang,
            HttpServletRequest request) {
        ReportService.ReportDownload download = exportService.export(projectId, planId,
                basePlanId, format, localeSupport.resolve(lang, request));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
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
