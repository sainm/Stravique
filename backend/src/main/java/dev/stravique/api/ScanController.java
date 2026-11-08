package dev.stravique.api;

import dev.stravique.api.dto.ScanComparisonDto;
import dev.stravique.api.dto.ScanDto;
import dev.stravique.application.ReportService;
import dev.stravique.application.ScanComparisonExportService;
import dev.stravique.application.ScanComparisonService;
import dev.stravique.application.ScanCoordinator;
import dev.stravique.config.RequestLocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ScanController {

    private final ScanCoordinator coordinator;
    private final ScanComparisonService comparisonService;
    private final ScanComparisonExportService comparisonExportService;
    private final RequestLocaleSupport localeSupport;

    public ScanController(ScanCoordinator coordinator, ScanComparisonService comparisonService,
                          ScanComparisonExportService comparisonExportService,
                          RequestLocaleSupport localeSupport) {
        this.coordinator = coordinator;
        this.comparisonService = comparisonService;
        this.comparisonExportService = comparisonExportService;
        this.localeSupport = localeSupport;
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

    @GetMapping("/projects/{projectId}/scans/{scanId}/comparison")
    public ScanComparisonDto comparison(@PathVariable UUID projectId, @PathVariable UUID scanId,
                                        @RequestParam(required = false) UUID baseScanId) {
        return comparisonService.compare(projectId, scanId, baseScanId);
    }

    @GetMapping("/projects/{projectId}/scans/{scanId}/comparison/export")
    public ResponseEntity<byte[]> comparisonExport(
            @PathVariable UUID projectId,
            @PathVariable UUID scanId,
            @RequestParam String format,
            @RequestParam(required = false) UUID baseScanId,
            @RequestParam(required = false) String lang,
            HttpServletRequest request) {
        ReportService.ReportDownload download = comparisonExportService.export(
                projectId, scanId, baseScanId, format, localeSupport.resolve(lang, request));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    @PostMapping("/scans/{scanId}/cancel")
    public ScanDto cancel(@PathVariable UUID scanId) {
        return coordinator.cancel(scanId);
    }
}
