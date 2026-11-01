package dev.stravique.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.api.ApiException;
import dev.stravique.api.dto.MigrationPlanComparisonDto;
import dev.stravique.api.dto.MigrationPlanComparisonFieldDto;
import dev.stravique.api.dto.MigrationPlanComparisonItemDto;
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
public class MigrationPlanComparisonExportService {

    private static final List<String> FORMATS = List.of("csv", "json", "markdown");
    private static final List<String> COLUMNS = List.of(
            "status", "itemKey", "itemId", "itemType", "targetKey", "title",
            "primaryFilePath", "phase", "automationLevel", "riskLevel", "itemStatus",
            "included", "changes");

    private final MigrationPlanService planService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public MigrationPlanComparisonExportService(MigrationPlanService planService,
                                                MessageSource messageSource,
                                                ObjectMapper objectMapper,
                                                AuditService auditService) {
        this.planService = planService;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ReportService.ReportDownload export(UUID projectId, UUID planId, UUID basePlanId,
                                               String format, Locale locale) {
        String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "MIGRATION_PLAN_COMPARISON_FORMAT_INVALID",
                    "error.migration.comparisonFormat");
        }
        MigrationPlanComparisonDto comparison = planService.comparison(projectId, planId, basePlanId);
        byte[] content = switch (normalized) {
            case "json" -> json(comparison);
            case "csv" -> withUtf8Bom(csv(comparison));
            default -> markdown(comparison, locale).getBytes(StandardCharsets.UTF_8);
        };
        String extension = normalized.equals("markdown") ? "md" : normalized;
        String contentType = switch (extension) {
            case "csv" -> "text/csv; charset=UTF-8";
            case "json" -> "application/json";
            default -> "text/markdown; charset=UTF-8";
        };
        String filename = "stravique-plan-comparison-"
                + comparison.plan().sequence() + "-vs-" + comparison.basePlan().sequence()
                + "." + extension;
        auditService.record(projectId, "MIGRATION_PLAN_COMPARISON_EXPORTED",
                "MIGRATION_PLAN", planId.toString(),
                normalized + ":" + comparison.items().size());
        return new ReportService.ReportDownload(content, contentType, filename);
    }

    private byte[] json(MigrationPlanComparisonDto comparison) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(comparison);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the plan comparison.", exception);
        }
    }

    private String csv(MigrationPlanComparisonDto comparison) {
        StringBuilder output = new StringBuilder();
        output.append(String.join(",", COLUMNS.stream().map(this::csvValue).toList()))
                .append('\n');
        for (Map<String, String> row : rows(comparison)) {
            output.append(String.join(",", COLUMNS.stream()
                    .map(column -> csvValue(row.get(column))).toList())).append('\n');
        }
        return output.toString();
    }

    private String markdown(MigrationPlanComparisonDto comparison, Locale locale) {
        StringBuilder output = new StringBuilder();
        output.append("# ").append(message("report.comparison.title", locale,
                "Migration plan comparison")).append("\n\n");
        output.append("- ").append(message("report.comparison.current", locale,
                "Current plan #{0}", comparison.plan().sequence())).append('\n');
        output.append("- ").append(message("report.comparison.base", locale,
                "Base plan #{0}", comparison.basePlan().sequence())).append("\n\n");
        output.append(message("report.comparison.summary", locale,
                "Added {0}, removed {1}, changed {2}, unchanged {3}.",
                comparison.summary().added(), comparison.summary().removed(),
                comparison.summary().changed(), comparison.summary().unchanged()))
                .append("\n\n");
        if (comparison.items().isEmpty()) {
            output.append(message("report.comparison.empty", locale,
                    "No item differences between these plans.")).append('\n');
            return output.toString();
        }
        output.append('|');
        for (String column : COLUMNS) {
            output.append(' ').append(escapeMarkdown(message(
                    "report.comparison.column." + column, locale, column))).append(" |");
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

    private List<Map<String, String>> rows(MigrationPlanComparisonDto comparison) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (MigrationPlanComparisonItemDto item : comparison.items()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("status", item.status());
            row.put("itemKey", item.itemKey());
            row.put("itemId", item.itemId() == null ? "" : item.itemId().toString());
            row.put("itemType", item.itemType());
            row.put("targetKey", item.targetKey());
            row.put("title", item.title());
            row.put("primaryFilePath", item.primaryFilePath() == null
                    ? "" : item.primaryFilePath());
            row.put("phase", Integer.toString(item.phase()));
            row.put("automationLevel", item.automationLevel());
            row.put("riskLevel", item.riskLevel());
            row.put("itemStatus", item.itemStatus());
            row.put("included", Boolean.toString(item.included()));
            row.put("changes", item.changes().stream()
                    .map(this::change)
                    .reduce((left, right) -> left + "; " + right)
                    .orElse(""));
            rows.add(row);
        }
        return rows;
    }

    private String change(MigrationPlanComparisonFieldDto change) {
        return change.field() + ":" + value(change.before()) + "->" + value(change.after());
    }

    private String value(String value) {
        return value == null ? "" : value;
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

    private String csvValue(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private String message(String key, Locale locale, String fallback, Object... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, fallback, locale);
    }
}
