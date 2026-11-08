package dev.stravique.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.api.ApiException;
import dev.stravique.api.dto.ScanComparisonDto;
import dev.stravique.api.dto.ScanComparisonFileDto;
import dev.stravique.api.dto.ScanComparisonRowDto;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanComparisonExportService {

    private static final List<String> FORMATS = List.of("csv", "json", "markdown");
    static final List<String> COLUMNS = List.of(
            "status", "relativePath", "beforeType", "afterType",
            "beforeSizeBytes", "afterSizeBytes", "beforeSha256", "afterSha256",
            "beforeEncoding", "afterEncoding", "beforeScanStatus", "afterScanStatus",
            "beforeParseStatus", "afterParseStatus");

    private final ScanComparisonService comparisonService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ScanComparisonExportService(ScanComparisonService comparisonService,
                                       MessageSource messageSource,
                                       ObjectMapper objectMapper,
                                       AuditService auditService) {
        this.comparisonService = comparisonService;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ReportService.ReportDownload export(UUID projectId, UUID scanId, UUID baseScanId,
                                               String format, Locale locale) {
        String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCAN_COMPARISON_FORMAT_INVALID",
                    "error.scan.comparisonFormat");
        }
        ScanComparisonDto comparison = comparisonService.compare(projectId, scanId, baseScanId);
        byte[] content = switch (normalized) {
            case "json" -> json(comparison);
            case "csv" -> withUtf8Bom(csv(rows(comparison)));
            default -> markdown(comparison, locale).getBytes(StandardCharsets.UTF_8);
        };
        String extension = normalized.equals("markdown") ? "md" : normalized;
        String contentType = switch (extension) {
            case "csv" -> "text/csv; charset=UTF-8";
            case "json" -> "application/json";
            default -> "text/markdown; charset=UTF-8";
        };
        String filename = "stravique-scan-comparison-"
                + comparison.scan().sequence() + "-vs-" + comparison.baseScan().sequence()
                + "." + extension;
        auditService.record(projectId, "SCAN_COMPARISON_EXPORTED", "SCAN", scanId.toString(),
                normalized + ":" + comparison.rows().size());
        return new ReportService.ReportDownload(content, contentType, filename);
    }

    private byte[] json(ScanComparisonDto comparison) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the scan comparison.", exception);
        }
    }

    static String csv(List<Map<String, String>> rows) {
        StringBuilder output = new StringBuilder();
        output.append(String.join(",", COLUMNS.stream()
                .map(ReportService::csvValue).toList())).append('\n');
        for (Map<String, String> row : rows) {
            output.append(String.join(",", COLUMNS.stream()
                    .map(column -> ReportService.csvValue(row.get(column))).toList())).append('\n');
        }
        return output.toString();
    }

    private String markdown(ScanComparisonDto comparison, Locale locale) {
        StringBuilder output = new StringBuilder();
        output.append("# ").append(message("report.scanComparison.title", locale,
                "Scan comparison")).append("\n\n");
        output.append("- ").append(message("report.scanComparison.current", locale,
                "Current scan #{0}", comparison.scan().sequence())).append('\n');
        output.append("- ").append(message("report.scanComparison.base", locale,
                "Base scan #{0}", comparison.baseScan().sequence())).append("\n\n");
        output.append(message("report.scanComparison.summary", locale,
                "Added {0}, removed {1}, changed {2}, unchanged {3}, unknown {4}.",
                comparison.summary().added(), comparison.summary().removed(),
                comparison.summary().changed(), comparison.summary().unchanged(),
                comparison.summary().unknown())).append("\n\n");
        if (comparison.truncated()) {
            output.append(message("report.scanComparison.truncated", locale,
                    "Only the first {0} changed rows are listed; the summary counts every row.",
                    comparison.rowLimit())).append("\n\n");
        }
        if (comparison.rows().isEmpty()) {
            output.append(message("report.scanComparison.empty", locale,
                    "No added, removed, changed, or unknown files between these scans."))
                    .append('\n');
            return output.toString();
        }
        output.append('|');
        for (String column : COLUMNS) {
            output.append(' ').append(escapeMarkdown(message(
                    "report.scanComparison.column." + column, locale, column))).append(" |");
        }
        output.append('\n').append('|');
        for (int index = 0; index < COLUMNS.size(); index++) {
            output.append(" --- |");
        }
        output.append('\n');
        for (Map<String, String> row : rows(comparison)) {
            output.append('|');
            for (String column : COLUMNS) {
                output.append(' ').append(escapeMarkdown(row.get(column))).append(" |");
            }
            output.append('\n');
        }
        return output.toString();
    }

    private List<Map<String, String>> rows(ScanComparisonDto comparison) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (ScanComparisonRowDto row : comparison.rows()) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("status", row.status());
            values.put("relativePath", row.relativePath());
            putFile(values, "before", row.before());
            putFile(values, "after", row.after());
            rows.add(values);
        }
        return rows;
    }

    private void putFile(Map<String, String> values, String prefix, ScanComparisonFileDto file) {
        values.put(prefix + "Type", file == null ? "" : file.fileType());
        values.put(prefix + "SizeBytes", file == null ? "" : Long.toString(file.sizeBytes()));
        values.put(prefix + "Sha256", file == null || file.sha256() == null ? "" : file.sha256());
        values.put(prefix + "Encoding", file == null || file.encoding() == null ? "" : file.encoding());
        values.put(prefix + "ScanStatus", file == null ? "" : file.scanStatus());
        values.put(prefix + "ParseStatus", file == null ? "" : file.parseStatus());
    }

    private byte[] withUtf8Bom(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = new byte[body.length + 3];
        encoded[0] = (byte) 0xEF;
        encoded[1] = (byte) 0xBB;
        encoded[2] = (byte) 0xBF;
        System.arraycopy(body, 0, encoded, 3, body.length);
        return encoded;
    }

    private String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private String message(String key, Locale locale, String fallback, Object... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, fallback, locale);
    }
}
