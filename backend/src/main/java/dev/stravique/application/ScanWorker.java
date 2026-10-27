package dev.stravique.application;

import dev.stravique.api.ApiException;
import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.AnalysisProject;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileChangeType;
import dev.stravique.domain.FileScanStatus;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ParseStatus;
import dev.stravique.domain.PathOverride;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.ProjectModule;
import dev.stravique.domain.ScanRun;
import dev.stravique.domain.ScanStatus;
import dev.stravique.infrastructure.AnalysisFindingRepository;
import dev.stravique.infrastructure.AnalysisProjectRepository;
import dev.stravique.infrastructure.PathOverrideRepository;
import dev.stravique.infrastructure.ProjectFileRepository;
import dev.stravique.infrastructure.ProjectModuleRepository;
import dev.stravique.infrastructure.ScanRunRepository;
import dev.stravique.infrastructure.TechnologyUsageRepository;
import dev.stravique.inventory.AnalysisResult;
import dev.stravique.inventory.AnalyzerContext;
import dev.stravique.inventory.FileRoleResolver;
import dev.stravique.inventory.InventoryFileAnalyzer;
import dev.stravique.inventory.ModuleDiscovery;
import dev.stravique.inventory.ScanPathPolicy;
import dev.stravique.inventory.TechEvidenceLedger;
import dev.stravique.inventory.TextDecoder;
import dev.stravique.inventory.TreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ScanWorker {

    private static final Logger log = LoggerFactory.getLogger(ScanWorker.class);
    private static final int FINDING_BATCH_SIZE = 250;

    private final ScanRunRepository scanRepository;
    private final AnalysisProjectRepository projectRepository;
    private final PathOverrideRepository overrideRepository;
    private final ProjectModuleRepository moduleRepository;
    private final ProjectFileRepository fileRepository;
    private final AnalysisFindingRepository findingRepository;
    private final TechnologyUsageRepository technologyRepository;
    private final ScanPathPolicy pathPolicy;
    private final TreeWalker treeWalker;
    private final ModuleDiscovery moduleDiscovery;
    private final TextDecoder textDecoder;
    private final InventoryFileAnalyzer fileAnalyzer;
    private final FileRoleResolver roleResolver;
    private final StraviqueProperties properties;
    private final AuditService auditService;

    public ScanWorker(ScanRunRepository scanRepository, AnalysisProjectRepository projectRepository,
                      PathOverrideRepository overrideRepository, ProjectModuleRepository moduleRepository,
                      ProjectFileRepository fileRepository, AnalysisFindingRepository findingRepository,
                      TechnologyUsageRepository technologyRepository, ScanPathPolicy pathPolicy,
                      TreeWalker treeWalker, ModuleDiscovery moduleDiscovery, TextDecoder textDecoder,
                      InventoryFileAnalyzer fileAnalyzer, FileRoleResolver roleResolver,
                      StraviqueProperties properties, AuditService auditService) {
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.overrideRepository = overrideRepository;
        this.moduleRepository = moduleRepository;
        this.fileRepository = fileRepository;
        this.findingRepository = findingRepository;
        this.technologyRepository = technologyRepository;
        this.pathPolicy = pathPolicy;
        this.treeWalker = treeWalker;
        this.moduleDiscovery = moduleDiscovery;
        this.textDecoder = textDecoder;
        this.fileAnalyzer = fileAnalyzer;
        this.roleResolver = roleResolver;
        this.properties = properties;
        this.auditService = auditService;
    }

    public void execute(UUID scanId, AtomicBoolean cancelRequested, Runnable completion) {
        ScanRun scan = scanRepository.findById(scanId).orElse(null);
        if (scan == null) {
            completion.run();
            return;
        }
        try {
            if (cancelRequested.get() || scan.getStatus() == ScanStatus.CANCEL_REQUESTED) {
                scan.cancel();
                scanRepository.save(scan);
                auditService.record("SCAN_CANCELLED", "SCAN", scan.getId().toString(), "queued");
                return;
            }
            scan.start();
            scanRepository.save(scan);

            AnalysisProject project = projectRepository.findById(scan.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("Project no longer exists."));
            Path root = pathPolicy.resolveExistingProjectDirectory(project.getSourcePath());
            List<PathOverride> overrides = overrideRepository.findAllByProjectIdOrderByRelativePathAsc(project.getId());
            TreeWalker.WalkResult walk = treeWalker.walk(root, lines(project.getExcludedDirectories()),
                    lines(project.getExcludedFiles()));
            ModuleDiscovery.ModuleLayout layout = moduleDiscovery.discover(root, walk, overrides);
            Map<String, ProjectModule> moduleEntities = persistModules(scan, layout);
            Map<String, ProjectFile> previousFiles = previousFiles(project.getId(), scan.getSequence());

            TechEvidenceLedger ledger = new TechEvidenceLedger();
            Set<String> seenPaths = new LinkedHashSet<>();
            List<AnalysisFinding> findingBatch = new ArrayList<>();
            int analyzed = 0;
            int parseFailures = 0;
            int skipped = 0;
            int frameworkFindings = 0;
            int reviewCount = 0;
            boolean cancelled = false;

            for (TreeWalker.DiscoveredFile discovered : walk.files()) {
                if (cancelRequested.get()) {
                    cancelled = true;
                    break;
                }
                ModuleDiscovery.ModuleSnapshot module = layout.moduleFor(discovered.relativePath());
                ProjectModule moduleEntity = moduleEntities.get(normalizeModule(module.relativePath()));
                String sha256 = sha256(discovered.absolutePath());
                ProjectFile previous = previousFiles.get(discovered.relativePath());
                FileChangeType changeType = previous == null ? FileChangeType.ADDED
                        : previous.getSha256().equals(sha256) ? FileChangeType.UNCHANGED
                        : FileChangeType.MODIFIED;
                ProjectFile file = ProjectFile.discovered(
                        scan.getId(),
                        project.getId(),
                        moduleEntity == null ? null : moduleEntity.getId(),
                        discovered.relativePath(),
                        discovered.absolutePath().getFileName().toString(),
                        discovered.fileType(),
                        discovered.size(),
                        discovered.lastModifiedAt(),
                        sha256,
                        discovered.generatedPath()
                );
                file.setChangeType(changeType);
                file.setStructureRole(roleResolver.resolve(discovered.relativePath(), discovered.fileType(),
                        module, overrides));
                seenPaths.add(discovered.relativePath());

                if (discovered.tooLarge()) {
                    file.setScanStatus(FileScanStatus.SKIPPED);
                    file.setParseStatus(ParseStatus.NOT_APPLICABLE);
                    file.setParseError("File exceeds the configured scan size limit.");
                    fileRepository.save(file);
                    skipped++;
                    continue;
                }
                if (discovered.generatedPath()) {
                    file.setScanStatus(FileScanStatus.SKIPPED);
                    file.setParseStatus(ParseStatus.NOT_APPLICABLE);
                    file.setParseError("Generated source path excluded from analysis.");
                    fileRepository.save(file);
                    skipped++;
                    continue;
                }

                TextDecoder.DecodedSource decoded = textDecoder.decode(discovered.absolutePath());
                file.setEncoding(decoded.encoding());
                if (decoded.hasGeneratedMarker()) {
                    file.setScanStatus(FileScanStatus.SKIPPED);
                    file.setParseStatus(ParseStatus.NOT_APPLICABLE);
                    file.setParseError("Generated source marker excluded from analysis.");
                    fileRepository.save(file);
                    skipped++;
                    continue;
                }
                if (!inAnalysisScope(project.getAnalysisScope(), discovered.fileType())) {
                    file.setScanStatus(FileScanStatus.SKIPPED);
                    file.setParseStatus(ParseStatus.NOT_APPLICABLE);
                    file.setParseError("File is outside the configured analysis scope.");
                    fileRepository.save(file);
                    skipped++;
                    continue;
                }

                file = fileRepository.save(file);
                AnalyzerContext context = new AnalyzerContext(
                        project.getId(),
                        scan.getId(),
                        file.getModuleId(),
                        file.getId(),
                        file.getRelativePath(),
                        file.getFileType(),
                        decoded.text(),
                        properties.getAnalyzerVersion(),
                        ledger
                );
                AnalysisResult result = fileAnalyzer.analyze(context);
                file.setParseStatus(result.parseStatus());
                file.setParseError(clip(stableParseError(result.parseError()), 4000));
                file.setFrameworkApiCount(Math.toIntExact(result.frameworkApiCount()));
                if (result.parseStatus() == ParseStatus.FAILED) {
                    file.setScanStatus(FileScanStatus.PARSE_FAILED);
                    parseFailures++;
                } else {
                    file.setScanStatus(FileScanStatus.ANALYZED);
                    analyzed++;
                }
                fileRepository.save(file);

                for (AnalysisFinding finding : result.findings()) {
                    findingBatch.add(finding);
                    frameworkFindings++;
                    if (isReviewState(finding.getCertainty())) {
                        reviewCount++;
                    }
                }
                if (findingBatch.size() >= FINDING_BATCH_SIZE) {
                    findingRepository.saveAll(findingBatch);
                    findingBatch.clear();
                }
            }

            List<ProjectFile> deletedFiles = new ArrayList<>();
            for (Map.Entry<String, ProjectFile> previous : previousFiles.entrySet()) {
                if (!seenPaths.contains(previous.getKey())) {
                    deletedFiles.add(ProjectFile.deleted(previous.getValue(), scan.getId(), project.getId()));
                }
            }
            if (!deletedFiles.isEmpty()) {
                fileRepository.saveAll(deletedFiles);
            }

            List<AnalysisFinding> diagnostics = diagnostics(scan, project, walk.diagnostics(), findingBatch.size());
            findingBatch.addAll(diagnostics);
            reviewCount += diagnostics.size();
            if (!findingBatch.isEmpty()) {
                findingRepository.saveAll(findingBatch);
            }

            technologyRepository.saveAll(ledger.toEntities(project.getId(), scan.getId(),
                    properties.getAnalyzerVersion()));
            int totalFiles = walk.files().size() + deletedFiles.size();
            scan.updateCounts(totalFiles, layout.modules().size(), analyzed, parseFailures, skipped,
                    frameworkFindings + diagnostics.size(), reviewCount, ledger.keys().size(),
                    deletedFiles.size());
            if (cancelled || cancelRequested.get()) {
                scan.cancel();
            } else {
                scan.complete();
            }
            scanRepository.save(scan);
            auditService.record(scan.getStatus() == ScanStatus.COMPLETED
                            ? "SCAN_COMPLETED" : "SCAN_CANCELLED",
                    "SCAN", scan.getId().toString(),
                    "files=" + totalFiles + ",findings=" + scan.getFindingCount());
            updateProject(project, layout, ledger, scan);
        } catch (ApiException exception) {
            fail(scanId, exception.getCode(), exception.getMessageKey(), exception);
        } catch (Exception exception) {
            fail(scanId, "SCAN_FAILED", "The scan failed before completion.", exception);
        } finally {
            completion.run();
        }
    }

    private Map<String, ProjectModule> persistModules(ScanRun scan, ModuleDiscovery.ModuleLayout layout) {
        Map<String, ProjectModule> result = new LinkedHashMap<>();
        List<ProjectModule> entities = layout.modules().stream().map(snapshot -> {
            ProjectModule module = ProjectModule.create(scan.getId(), scan.getProjectId(),
                    normalizeModule(snapshot.relativePath()), snapshot.name());
            module.setSourceRoots(join(snapshot.sourceRoots()));
            module.setTestRoots(join(snapshot.testRoots()));
            module.setResourceRoots(join(snapshot.resourceRoots()));
            module.setWebRoots(join(snapshot.webRoots()));
            module.setConfigRoots(join(snapshot.configRoots()));
            module.setSqlRoots(join(snapshot.sqlRoots()));
            return module;
        }).toList();
        moduleRepository.saveAll(entities).forEach(module ->
                result.put(normalizeModule(module.getRelativePath()), module));
        return result;
    }

    private Map<String, ProjectFile> previousFiles(UUID projectId, int sequence) {
        return scanRepository.findFirstByProjectIdAndStatusInOrderBySequenceDesc(
                        projectId, List.of(ScanStatus.COMPLETED))
                .filter(previous -> previous.getSequence() < sequence)
                .map(previous -> fileRepository.findByScanIdOrderByRelativePathAsc(previous.getId()).stream()
                        .filter(file -> file.getChangeType() != FileChangeType.DELETED)
                        .collect(java.util.stream.Collectors.toMap(ProjectFile::getRelativePath, file -> file,
                                (left, right) -> left, LinkedHashMap::new)))
                .orElseGet(LinkedHashMap::new);
    }

    private List<AnalysisFinding> diagnostics(ScanRun scan, AnalysisProject project,
                                              List<TreeWalker.Diagnostic> diagnostics, int batchSize) {
        return diagnostics.stream().map(diagnostic -> AnalysisFinding.create(
                project.getId(),
                scan.getId(),
                null,
                null,
                diagnostic.relativePath(),
                null,
                null,
                AnalysisKind.REVIEW_ITEM,
                diagnostic.code(),
                diagnostic.message(),
                "TREE-" + diagnostic.code(),
                diagnostic.message(),
                diagnostic.code().endsWith("FAILED") ? Certainty.PARSE_FAILED : Certainty.REVIEW_REQUIRED,
                properties.getAnalyzerVersion(),
                diagnostic.code().endsWith("FAILED") ? ParseStatus.FAILED : ParseStatus.NOT_APPLICABLE,
                "{\"batch\":" + batchSize + "}"
        )).toList();
    }

    private void updateProject(AnalysisProject project, ModuleDiscovery.ModuleLayout layout,
                               TechEvidenceLedger ledger, ScanRun scan) {
        if (project.getProjectType() == dev.stravique.domain.ProjectType.UNKNOWN) {
            project.setProjectType(layout.projectType());
        }
        if (project.getBuildTool() == dev.stravique.domain.BuildTool.UNKNOWN) {
            project.setBuildTool(layout.buildTool());
        }
        if (project.getJdkVersion() == null) {
            project.setJdkVersion(layout.jdkVersion());
        }
        if (project.getSourceFramework() == null) {
            Set<String> frameworkKeys = ledger.keys();
            List<String> selected = new ArrayList<>();
            for (String key : List.of("SASTRUTS", "STRUTS1", "SEASAR2", "S2JDBC")) {
                if (frameworkKeys.contains(key)) {
                    selected.add(key);
                }
            }
            if (!selected.isEmpty()) {
                project.setSourceFramework(String.join(", ", selected));
            }
        }
        project.setLastScanAt(scan.getFinishedAt() == null ? Instant.now() : scan.getFinishedAt());
        project.touch();
        projectRepository.save(project);
    }

    private void fail(UUID scanId, String code, String message, Exception exception) {
        log.warn("Scan {} failed with code {}", scanId, code);
        log.debug("Scan failure detail", exception);
        scanRepository.findById(scanId).ifPresent(scan -> {
            scan.fail(code, message);
            scanRepository.save(scan);
            auditService.record("SCAN_FAILED", "SCAN", scanId.toString(), code);
        });
    }

    private boolean isReviewState(Certainty certainty) {
        return certainty == Certainty.REVIEW_REQUIRED || certainty == Certainty.UNRESOLVED
                || certainty == Certainty.PARSE_FAILED;
    }

    private boolean inAnalysisScope(String scope, FileType type) {
        if (scope == null || scope.isBlank() || scope.equals("FULL")) {
            return true;
        }
        if (scope.equals("SOURCE_ONLY")) {
            return type == FileType.JAVA || type == FileType.TEST || type == FileType.JSP
                    || type == FileType.HTML || type == FileType.JAVASCRIPT
                    || type == FileType.CSS || type == FileType.SQL;
        }
        if (scope.equals("CONFIGURATION_ONLY")) {
            return type == FileType.XML || type == FileType.PROPERTIES || type == FileType.YAML
                    || type == FileType.DICON || type == FileType.TLD
                    || type == FileType.MAVEN_POM || type == FileType.GRADLE;
        }
        return true;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<String> lines(String value) {
        return value == null || value.isBlank() ? List.of() : value.lines().toList();
    }

    private String join(List<String> values) {
        return String.join("\n", values);
    }

    private String normalizeModule(String value) {
        return value == null || value.isBlank() ? "." : value;
    }

    private String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String stableParseError(String value) {
        if (value == null) {
            return null;
        }
        String singleLine = value.replaceAll("\\s+", " ");
        if (singleLine.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
            return "Parser rejected the input; verify syntax and supported features.";
        }
        return singleLine;
    }
}
