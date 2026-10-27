package dev.stravique.api;

import dev.stravique.api.dto.ReportDescriptorDto;
import dev.stravique.application.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<ReportDescriptorDto> descriptors(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID scanId) {
        return reportService.descriptors(projectId, scanId);
    }

    @GetMapping("/{type}.{format}")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID projectId,
            @PathVariable String type,
            @PathVariable String format,
            @RequestParam(required = false) UUID scanId) {
        ReportService.ReportDownload download = reportService.export(projectId, scanId, type, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }
}
