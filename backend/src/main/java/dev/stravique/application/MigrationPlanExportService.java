package dev.stravique.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.api.ApiException;
import dev.stravique.api.dto.MigrationDependencyDto;
import dev.stravique.api.dto.MigrationPhaseDto;
import dev.stravique.api.dto.MigrationPlanExportDto;
import dev.stravique.api.dto.MigrationPlanExportItemDto;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MigrationPlanExportService {

    private static final List<String> FORMATS = List.of("csv", "json", "markdown");
    private static final List<String> ITEM_COLUMNS = List.of(
            "planId", "planSequence", "projectId", "scanId", "sourceScanSequence",
            "sourceProfile", "targetProfile", "itemId", "itemKey", "itemType", "targetKey",
            "title", "primaryFileId", "primaryFilePath", "primaryFindingId", "phase",
            "priority", "automationLevel", "riskLevel", "status", "included", "reasonCode",
            "findingCount", "dependencyCount", "dependentCount", "dependencyEvidence"
    );

    private final MigrationPlanService planService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public MigrationPlanExportService(MigrationPlanService planService,
                                      MessageSource messageSource,
                                      ObjectMapper objectMapper,
                                      AuditService auditService) {
        this.planService = planService;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public ReportService.ReportDownload export(UUID projectId, UUID planId,
                                               String format, Locale locale) {
        String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "MIGRATION_PLAN_EXPORT_FORMAT_INVALID",
                    "error.migration.planExportFormat");
        }
        MigrationPlanExportDto data = planService.exportData(projectId, planId);
        byte[] content = switch (normalized) {
            case "json" -> json(data);
            case "csv" -> withUtf8Bom(csv(data));
            default -> markdown(data, locale).getBytes(StandardCharsets.UTF_8);
        };
        String extension = normalized.equals("markdown") ? "md" : normalized;
        String contentType = switch (extension) {
            case "csv" -> "text/csv; charset=UTF-8";
            case "json" -> "application/json";
            default -> "text/markdown; charset=UTF-8";
        };
        String filename = "stravique-migration-plan-" + data.plan().sequence() + "." + extension;
        auditService.record(projectId, "MIGRATION_PLAN_EXPORTED", "MIGRATION_PLAN",
                planId.toString(), normalized + ":" + data.items().size());
        return new ReportService.ReportDownload(content, contentType, filename);
    }

    private byte[] json(MigrationPlanExportDto data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize the migration plan.", exception);
        }
    }

    private String csv(MigrationPlanExportDto data) {
        StringBuilder output = new StringBuilder();
        output.append(String.join(",", ITEM_COLUMNS.stream().map(this::csvValue).toList()))
                .append('\n');
        for (MigrationPlanExportItemDto item : data.items()) {
            Map<String, String> row = itemRow(data, item);
            output.append(String.join(",", ITEM_COLUMNS.stream()
                    .map(column -> csvValue(row.get(column))).toList())).append('\n');
        }
        return output.toString();
    }

    private String markdown(MigrationPlanExportDto data, Locale locale) {
        StringBuilder output = new StringBuilder();
        output.append("# ").append(message("report.plan.title", locale,
                "Migration plan #{0}", data.plan().sequence())).append("\n\n");
        output.append("- ").append(message("report.plan.sourceProfile", locale,
                "Source profile: {0}", data.plan().sourceProfile())).append('\n');
        output.append("- ").append(message("report.plan.targetProfile", locale,
                "Target profile: {0}", data.plan().targetProfile())).append('\n');
        output.append("- ").append(message("report.plan.sourceScan", locale,
                "Source scan: #{0}", data.plan().sourceScanSequence())).append('\n');
        output.append("- ").append(message("report.plan.exportedAt", locale,
                "Exported at: {0}", data.exportedAt())).append("\n\n");
        output.append(message("report.plan.summary", locale,
                "Items: {0}; included: {1}; ready: {2}; review: {3}; blocked: {4}; dependencies: {5}; phases: {6}.",
                data.plan().itemCount(), data.plan().includedCount(), data.plan().readyCount(),
                data.plan().reviewCount(), data.plan().blockedCount(),
                data.plan().dependencyCount(), data.plan().phaseCount())).append("\n\n");

        output.append("## ").append(message("report.plan.phases", locale, "Phases"))
                .append("\n\n");
        appendTable(output, List.of("phase", "itemCount", "readyCount", "reviewCount", "blockedCount"),
                data.phases().stream().map(phase -> phaseRow(phase)).toList(), locale, "phase");
        output.append('\n');

        output.append("## ").append(message("report.plan.items", locale, "Items"))
                .append("\n\n");
        appendTable(output, ITEM_COLUMNS,
                data.items().stream().map(item -> itemRow(data, item)).toList(),
                locale, "item");
        output.append('\n');

        output.append("## ").append(message("report.plan.dependencies", locale, "Dependencies"))
                .append("\n\n");
        if (data.dependencies().isEmpty()) {
            output.append(message("report.plan.noDependencies", locale,
                    "No persisted dependencies.")).append('\n');
        } else {
            appendTable(output,
                    List.of("fromTitle", "toTitle", "relationType", "evidencePath", "lineStart", "certainty"),
                    data.dependencies().stream().map(this::dependencyRow).toList(),
                    locale, "dependency");
        }
        return output.toString();
    }

    private void appendTable(StringBuilder output, List<String> columns,
                             List<Map<String, String>> rows, Locale locale, String section) {
        output.append('|');
        for (String column : columns) {
            output.append(' ').append(escapeMarkdown(message(
                    "report.plan.column." + section + "." + column, locale, column))).append(" |");
        }
        output.append('\n').append('|');
        for (int index = 0; index < columns.size(); index++) {
            output.append(" --- |");
        }
        output.append('\n');
        for (Map<String, String> row : rows) {
            output.append('|');
            for (String column : columns) {
                output.append(' ').append(escapeMarkdown(row.getOrDefault(column, ""))).append(" |");
            }
            output.append('\n');
        }
    }

    private Map<String, String> itemRow(MigrationPlanExportDto data,
                                        MigrationPlanExportItemDto item) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("planId", data.plan().id().toString());
        row.put("planSequence", Integer.toString(data.plan().sequence()));
        row.put("projectId", data.plan().projectId().toString());
        row.put("scanId", data.plan().scanId().toString());
        row.put("sourceScanSequence", Integer.toString(data.plan().sourceScanSequence()));
        row.put("sourceProfile", data.plan().sourceProfile());
        row.put("targetProfile", data.plan().targetProfile());
        row.put("itemId", item.id().toString());
        row.put("itemKey", item.itemKey());
        row.put("itemType", item.itemType());
        row.put("targetKey", item.targetKey());
        row.put("title", item.title());
        row.put("primaryFileId", value(item.primaryFileId()));
        row.put("primaryFilePath", value(item.primaryFilePath()));
        row.put("primaryFindingId", value(item.primaryFindingId()));
        row.put("phase", Integer.toString(item.phase()));
        row.put("priority", Integer.toString(item.priority()));
        row.put("automationLevel", item.automationLevel());
        row.put("riskLevel", item.riskLevel());
        row.put("status", item.status());
        row.put("included", Boolean.toString(item.included()));
        row.put("reasonCode", item.reasonCode());
        row.put("findingCount", Integer.toString(item.findingCount()));
        row.put("dependencyCount", Integer.toString(item.dependencyCount()));
        row.put("dependentCount", Integer.toString(item.dependentCount()));
        row.put("dependencyEvidence", data.dependencies().stream()
                .filter(dependency -> dependency.fromItemId().equals(item.id()))
                .map(this::dependencyText)
                .reduce((left, right) -> left + "; " + right).orElse(""));
        return row;
    }

    private Map<String, String> phaseRow(MigrationPhaseDto phase) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("phase", Integer.toString(phase.phase()));
        row.put("itemCount", Integer.toString(phase.itemCount()));
        row.put("readyCount", Integer.toString(phase.readyCount()));
        row.put("reviewCount", Integer.toString(phase.reviewCount()));
        row.put("blockedCount", Integer.toString(phase.blockedCount()));
        return row;
    }

    private Map<String, String> dependencyRow(MigrationDependencyDto dependency) {
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put("fromTitle", dependency.fromTitle());
        row.put("toTitle", dependency.toTitle());
        row.put("relationType", dependency.relationType());
        row.put("evidencePath", dependency.evidencePath());
        row.put("lineStart", value(dependency.lineStart()));
        row.put("certainty", dependency.certainty());
        return row;
    }

    private String dependencyText(MigrationDependencyDto dependency) {
        return dependency.toTitle() + " [" + dependency.relationType() + "] "
                + dependency.evidencePath()
                + (dependency.lineStart() == null ? "" : ":" + dependency.lineStart());
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
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
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private String message(String key, Locale locale, String fallback, Object... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, fallback, locale);
    }
}
