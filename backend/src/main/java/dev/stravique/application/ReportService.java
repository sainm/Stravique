package dev.stravique.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.api.ApiException;
import dev.stravique.api.dto.ReportDescriptorDto;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ScanRun;
import dev.stravique.infrastructure.AnalysisFindingRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ProjectModuleRepository;
import dev.stravique.infrastructure.ModuleDependencyRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.infrastructure.TechnologyUsageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private static final List<String> FORMATS = List.of("csv", "json", "markdown");
    private static final Set<Certainty> REVIEW_STATES = Set.of(
            Certainty.REVIEW_REQUIRED, Certainty.UNRESOLVED, Certainty.PARSE_FAILED
    );

    private final AnalysisProjectRepository projectRepository;
    private final ScanRunRepository scanRepository;
    private final ProjectFileRepository fileRepository;
    private final ProjectModuleRepository moduleRepository;
    private final ModuleDependencyRepository moduleDependencyRepository;
    private final AnalysisFindingRepository findingRepository;
    private final TechnologyUsageRepository technologyRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public ReportService(AnalysisProjectRepository projectRepository, ScanRunRepository scanRepository,
                         ProjectFileRepository fileRepository, ProjectModuleRepository moduleRepository,
                         ModuleDependencyRepository moduleDependencyRepository,
                         AnalysisFindingRepository findingRepository,
                         TechnologyUsageRepository technologyRepository, AuditService auditService,
                         ObjectMapper objectMapper, MessageSource messageSource) {
        this.projectRepository = projectRepository;
        this.scanRepository = scanRepository;
        this.fileRepository = fileRepository;
        this.moduleRepository = moduleRepository;
        this.moduleDependencyRepository = moduleDependencyRepository;
        this.findingRepository = findingRepository;
        this.technologyRepository = technologyRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public List<ReportDescriptorDto> descriptors(UUID projectId, UUID requestedScanId) {
        requireProject(projectId);
        ScanRun scan = resolveScan(projectId, requestedScanId);
        if (scan == null) {
            return Arrays.stream(ReportType.values())
                    .map(type -> new ReportDescriptorDto(null, type.name(), 0, FORMATS))
                    .toList();
        }
        return Arrays.stream(ReportType.values())
                .map(type -> new ReportDescriptorDto(scan.getId(), type.name(),
                        reportRows(type, scan).size(), FORMATS))
                .toList();
    }

    @Transactional
    public ReportDownload export(UUID projectId, UUID requestedScanId, String reportType,
                                 String format, Locale locale) {
        AnalysisProject project = requireProject(projectId);
        ScanRun scan = resolveScan(projectId, requestedScanId);
        ReportType type = ReportType.parse(reportType);
        if (!FORMATS.contains(format.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_FORMAT_INVALID",
                    "error.report.format");
        }
        List<LinkedHashMap<String, Object>> rows = scan == null ? List.of() : reportRows(type, scan);
        byte[] content = serialize(rows, format.toLowerCase(Locale.ROOT), type, locale);
        String extension = format.equalsIgnoreCase("markdown") ? "md" : format.toLowerCase(Locale.ROOT);
        String contentType = switch (extension) {
            case "csv" -> "text/csv; charset=UTF-8";
            case "json" -> "application/json";
            default -> "text/markdown; charset=UTF-8";
        };
        String filename = "stravique-" + slug(project.getName()) + "-"
                + type.name().toLowerCase(Locale.ROOT).replace('_', '-') + "-"
                + (scan == null ? "no-scan" : scan.getSequence()) + "." + extension;
        auditService.record(projectId, "REPORT_EXPORTED", "PROJECT", projectId.toString(),
                type.name() + ":" + extension + ":" + rows.size());
        return new ReportDownload(content, contentType, filename);
    }

    private List<LinkedHashMap<String, Object>> reportRows(ReportType type, ScanRun scan) {
        return switch (type) {
            case FILES -> files(scan);
            case STRUCTURE -> structure(scan);
            case TECHNOLOGY -> technology(scan);
            case FRAMEWORKS -> frameworkFindings(scan);
            case CONFIGURATION -> findingRows(findingRepository
                    .findByScanIdAndAnalysisKindOrderByFilePathAscLineStartAsc(
                            scan.getId(), AnalysisKind.CONFIGURATION));
            case PARSE_ERRORS -> findingRepository.findByScanIdOrderByFilePathAscLineStartAsc(scan.getId())
                    .stream().filter(finding -> finding.getCertainty() == Certainty.PARSE_FAILED
                            || finding.getAnalysisKind() == AnalysisKind.PARSE_ERROR)
                    .map(this::findingRow).toList();
            case REVIEWS -> findingRepository.findByScanIdOrderByFilePathAscLineStartAsc(scan.getId())
                    .stream().filter(finding -> REVIEW_STATES.contains(finding.getCertainty()))
                    .map(this::findingRow).toList();
        };
    }

    private List<LinkedHashMap<String, Object>> files(ScanRun scan) {
        return fileRepository.findByScanIdOrderByRelativePathAsc(scan.getId()).stream().map(file -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", file.getId());
            row.put("path", file.getRelativePath());
            row.put("type", file.getFileType().name());
            row.put("moduleId", file.getModuleId());
            row.put("sizeBytes", file.getSizeBytes());
            row.put("encoding", file.getEncoding());
            row.put("lastModifiedAt", file.getLastModifiedAt());
            row.put("scanStatus", file.getScanStatus().name());
            row.put("parseStatus", file.getParseStatus().name());
            row.put("parseError", file.getParseError());
            row.put("frameworkApiCount", file.getFrameworkApiCount());
            row.put("generated", file.isGenerated());
            row.put("changeType", file.getChangeType().name());
            row.put("structureRole", file.getStructureRole());
            row.put("sha256", file.getSha256());
            return row;
        }).toList();
    }

    private List<LinkedHashMap<String, Object>> structure(ScanRun scan) {
        var modules = moduleRepository.findByScanIdOrderByRelativePathAsc(scan.getId());
        Map<UUID, String> modulePaths = modules.stream().collect(java.util.stream.Collectors.toMap(
                module -> module.getId(), module -> module.getRelativePath()));
        Map<UUID, List<String>> dependenciesBySource = new LinkedHashMap<>();
        moduleDependencyRepository.findByScanIdOrderByEvidencePathAscLineStartAsc(scan.getId())
                .forEach(dependency -> dependenciesBySource.computeIfAbsent(
                        dependency.getFromModuleId(), ignored -> new ArrayList<>())
                        .add(modulePaths.getOrDefault(dependency.getToModuleId(), "")
                                + " (" + dependency.getEvidencePath()
                                + ":" + dependency.getLineStart() + ")"));
        return modules.stream().map(module -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", module.getId());
            row.put("path", module.getRelativePath());
            row.put("name", module.getName());
            row.put("sourceRoots", module.getSourceRoots());
            row.put("testRoots", module.getTestRoots());
            row.put("resourceRoots", module.getResourceRoots());
            row.put("webRoots", module.getWebRoots());
            row.put("configRoots", module.getConfigRoots());
            row.put("sqlRoots", module.getSqlRoots());
            row.put("moduleDependencies", String.join(", ", dependenciesBySource
                    .getOrDefault(module.getId(), List.of())));
            return row;
        }).toList();
    }

    private List<LinkedHashMap<String, Object>> technology(ScanRun scan) {
        return technologyRepository.findByScanIdOrderByCategoryAscNameAsc(scan.getId()).stream().map(item -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("technologyKey", item.getTechnologyKey());
            row.put("name", item.getName());
            row.put("category", item.getCategory().name());
            row.put("status", item.getStatus().name());
            row.put("apiEvidenceCount", item.getApiEvidenceCount());
            row.put("configurationEvidenceCount", item.getConfigurationEvidenceCount());
            row.put("dependencyEvidenceCount", item.getDependencyEvidenceCount());
            row.put("totalEvidenceCount", item.getTotalEvidenceCount());
            row.put("ruleIds", item.getRuleIds());
            row.put("firstEvidencePath", item.getFirstEvidencePath());
            row.put("firstEvidenceLine", item.getFirstEvidenceLine());
            row.put("analyzerVersion", item.getAnalyzerVersion());
            row.put("analyzedAt", item.getAnalyzedAt());
            return row;
        }).toList();
    }

    private List<LinkedHashMap<String, Object>> frameworkFindings(ScanRun scan) {
        Set<String> technologyKeys = new LinkedHashSet<>();
        technologyRepository.findByScanIdOrderByCategoryAscNameAsc(scan.getId())
                .forEach(item -> technologyKeys.add(item.getTechnologyKey()));
        return findingRepository.findByScanIdOrderByFilePathAscLineStartAsc(scan.getId()).stream()
                .filter(finding -> technologyKeys.contains(finding.getObjectKey()))
                .filter(finding -> finding.getAnalysisKind() == AnalysisKind.FRAMEWORK_API
                        || finding.getAnalysisKind() == AnalysisKind.CONFIGURATION)
                .map(this::findingRow).toList();
    }

    private List<LinkedHashMap<String, Object>> findingRows(List<AnalysisFinding> findings) {
        return findings.stream().map(this::findingRow).toList();
    }

    private LinkedHashMap<String, Object> findingRow(AnalysisFinding finding) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", finding.getId());
        row.put("path", finding.getFilePath());
        row.put("lineStart", finding.getLineStart());
        row.put("lineEnd", finding.getLineEnd());
        row.put("analysisKind", finding.getAnalysisKind().name());
        row.put("objectKey", finding.getObjectKey());
        row.put("summary", finding.getSummary());
        row.put("ruleId", finding.getRuleId());
        row.put("evidence", finding.getEvidence());
        row.put("certainty", finding.getCertainty().name());
        row.put("analyzerVersion", finding.getAnalyzerVersion());
        row.put("analyzedAt", finding.getAnalyzedAt());
        row.put("parseStatus", finding.getParseStatus().name());
        row.put("metadata", finding.getMetadata());
        return row;
    }

    private static final List<String> FILE_COLUMNS = List.of(
            "id", "path", "type", "moduleId", "sizeBytes", "encoding", "lastModifiedAt",
            "scanStatus", "parseStatus", "parseError", "frameworkApiCount", "generated",
            "changeType", "structureRole", "sha256");

    private static final List<String> STRUCTURE_COLUMNS = List.of(
            "id", "path", "name", "sourceRoots", "testRoots", "resourceRoots", "webRoots",
            "configRoots", "sqlRoots", "moduleDependencies");

    private static final List<String> TECHNOLOGY_COLUMNS = List.of(
            "id", "technologyKey", "name", "category", "status", "apiEvidenceCount",
            "configurationEvidenceCount", "dependencyEvidenceCount", "totalEvidenceCount",
            "ruleIds", "firstEvidencePath", "firstEvidenceLine", "analyzerVersion", "analyzedAt");

    private static final List<String> FINDING_COLUMNS = List.of(
            "id", "path", "lineStart", "lineEnd", "analysisKind", "objectKey", "summary",
            "ruleId", "evidence", "certainty", "analyzerVersion", "analyzedAt", "parseStatus",
            "metadata");

    private static List<String> columns(ReportType type) {
        return switch (type) {
            case FILES -> FILE_COLUMNS;
            case STRUCTURE -> STRUCTURE_COLUMNS;
            case TECHNOLOGY -> TECHNOLOGY_COLUMNS;
            case FRAMEWORKS, CONFIGURATION, PARSE_ERRORS, REVIEWS -> FINDING_COLUMNS;
        };
    }

    private byte[] serialize(List<LinkedHashMap<String, Object>> rows, String format,
                             ReportType type, Locale locale) {
        if (format.equals("json")) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize report.", exception);
            }
        }
        if (format.equals("csv")) {
            return withUtf8Bom(csv(rows, type));
        }
        return markdown(rows, type, locale).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] withUtf8Bom(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        if (body.length == 0) {
            return body;
        }
        byte[] encoded = new byte[body.length + 3];
        encoded[0] = (byte) 0xEF;
        encoded[1] = (byte) 0xBB;
        encoded[2] = (byte) 0xBF;
        System.arraycopy(body, 0, encoded, 3, body.length);
        return encoded;
    }

    private String csv(List<LinkedHashMap<String, Object>> rows, ReportType type) {
        List<String> headers = columns(type);
        StringBuilder output = new StringBuilder();
        output.append(String.join(",", headers.stream().map(this::csvValue).toList())).append('\n');
        for (Map<String, Object> row : rows) {
            output.append(String.join(",", headers.stream()
                    .map(header -> csvValue(value(row.get(header)))).toList())).append('\n');
        }
        return output.toString();
    }

    private String markdown(List<LinkedHashMap<String, Object>> rows, ReportType type, Locale locale) {
        List<String> headers = columns(type);
        String title = message("report.title." + type.name(), locale, type.name());
        if (rows.isEmpty()) {
            return "# " + title + "\n\n"
                    + message("report.markdownEmpty", locale,
                    "No persisted rows are available for this report.") + "\n";
        }
        StringBuilder output = new StringBuilder("# ").append(title).append("\n\n");
        output.append('|');
        for (String header : headers) {
            output.append(' ').append(escapeMarkdown(
                    message("report.column." + header, locale, header))).append(" |");
        }
        output.append('\n').append('|');
        for (int index = 0; index < headers.size(); index++) {
            output.append(" --- |");
        }
        output.append('\n');
        for (Map<String, Object> row : rows) {
            output.append('|');
            for (String header : headers) {
                output.append(' ').append(escapeMarkdown(value(row.get(header)))).append(" |");
            }
            output.append('\n');
        }
        return output.toString();
    }

    private String csvValue(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String value(Object value) {
        return value == null ? "" : value instanceof Instant instant ? instant.toString() : value.toString();
    }

    private String escapeMarkdown(String value) {
        return value.replace("|", "\\|").replace("\n", " ")
                .replace("\r", " ");
    }

    private ScanRun resolveScan(UUID projectId, UUID requestedScanId) {
        if (requestedScanId == null) {
            return scanRepository.findFirstByProjectIdOrderBySequenceDesc(projectId).orElse(null);
        }
        ScanRun scan = scanRepository.findById(requestedScanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                        "error.scan.notFound"));
        if (!scan.getProjectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND",
                    "error.scan.notFound");
        }
        return scan;
    }

    private AnalysisProject requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND",
                        "error.project.notFound"));
    }

    private String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "project" : slug;
    }

    private String message(String key, Locale locale, String fallback) {
        return messageSource.getMessage(key, null, fallback, locale);
    }

    public record ReportDownload(byte[] content, String contentType, String filename) {
    }

    private enum ReportType {
        FILES,
        STRUCTURE,
        TECHNOLOGY,
        FRAMEWORKS,
        CONFIGURATION,
        PARSE_ERRORS,
        REVIEWS;

        private static ReportType parse(String value) {
            try {
                return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND",
                        "error.report.notFound");
            }
        }
    }
}
