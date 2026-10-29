package dev.stravique.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisRelation;
import dev.stravique.domain.AnalysisRelationType;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileChangeType;
import dev.stravique.domain.FileScanStatus;
import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationAutomation;
import dev.stravique.domain.MigrationItemStatus;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationRisk;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TechnologyUsage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class MigrationPlanner {

    static final Set<AnalysisRelationType> DEPENDENCY_TYPES = Set.of(
            AnalysisRelationType.JAVA_IMPORT,
            AnalysisRelationType.JAVA_INHERITANCE,
            AnalysisRelationType.SEASAR_BINDING,
            AnalysisRelationType.SEASAR_DICON_INJECTION,
            AnalysisRelationType.CONFIG_CLASS_REFERENCE,
            AnalysisRelationType.DAO_SQL_REFERENCE,
            AnalysisRelationType.MESSAGE_PRODUCTION,
            AnalysisRelationType.MESSAGE_DISPLAY,
            AnalysisRelationType.JSP_FORM_TO_ACTION
    );

    private static final Set<String> ACTION_RULES = Set.of(
            "JAVA-SAST-ACTION-CLASS-001", "JAVA-SAST-EXECUTE-001", "JAVA-SAST-EXECUTE-002",
            "JAVA-STR1-ACTION-001", "JAVA-STR1-EXECUTE-001", "JAVA-STR1-VALIDATION-001",
            "JAVA-STR1-FORWARD-001"
    );
    private static final Set<String> FORM_RULES = Set.of(
            "JAVA-SAST-FORM-001", "JAVA-STR1-FORM-001"
    );
    private static final Set<String> COMPONENT_RULES = Set.of(
            "JAVA-SEASAR-ANN-001"
    );
    private static final Set<String> DATA_RULES = Set.of(
            "JAVA-S2JDBC-ENTITY-001", "JAVA-S2JDBC-TABLE-001", "JAVA-S2JDBC-DAO-001",
            "JAVA-S2JDBC-QUERY-001", "JAVA-S2JDBC-TX-001", "SQL-STATEMENT-001"
    );
    private static final Set<String> MESSAGE_RULES = Set.of(
            "JAVA-STR1-MESSAGE-001"
    );
    private static final Set<String> TRANSACTION_RULES = Set.of(
            "JAVA-S2JDBC-TX-001"
    );
    private static final Set<String> VALIDATION_RULES = Set.of(
            "JAVA-SAST-VALIDATION-001"
    );
    private static final Set<String> JAKARTA_RULES = Set.of(
            "JAVA-JAKARTA-NAMESPACE-001"
    );
    private static final Set<String> UNRESOLVED_RULES = Set.of(
            "JAVA-DYNAMIC-001", "JAVA-REFLECTION-001"
    );
    private static final Set<String> CUSTOM_FRAMEWORK_RULES = Set.of(
            "JAVA-CUSTOM-FRAMEWORK-001"
    );
    private static final Set<String> CUSTOM_TAG_RULES = Set.of(
            "JSP-CUSTOM-TAGLIB-001", "JSP-CUSTOM-TAG-USE-001"
    );
    private static final List<String> SOURCE_PROFILE_ORDER = List.of(
            "SASTRUTS", "STRUTS1", "SEASAR2", "S2JDBC", "SERVLET", "JSP", "JSTL", "CUSTOM_TAGS"
    );
    private static final Map<MigrationItemType, Integer> PREFERENCE = Map.ofEntries(
            Map.entry(MigrationItemType.ACTION_CONTROLLER, 1),
            Map.entry(MigrationItemType.MESSAGE_FLOW, 2),
            Map.entry(MigrationItemType.COMPONENT_BEAN, 3),
            Map.entry(MigrationItemType.DATA_ACCESS, 4),
            Map.entry(MigrationItemType.FORM_MODEL, 5),
            Map.entry(MigrationItemType.VALIDATION, 6),
            Map.entry(MigrationItemType.CONFIGURATION, 7),
            Map.entry(MigrationItemType.MESSAGE_RESOURCE, 8),
            Map.entry(MigrationItemType.JAKARTA_NAMESPACE, 9),
            Map.entry(MigrationItemType.JSP_VIEW, 10),
            Map.entry(MigrationItemType.REVIEW, 11)
    );

    private final ObjectMapper objectMapper;

    public MigrationPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Draft plan(List<ProjectFile> files, List<AnalysisFinding> findings,
                      List<AnalysisRelation> relations, List<TechnologyUsage> technologies) {
        Map<UUID, ProjectFile> fileById = new LinkedHashMap<>();
        for (ProjectFile file : files) {
            fileById.put(file.getId(), file);
        }
        Map<UUID, List<AnalysisFinding>> findingsByFile = new LinkedHashMap<>();
        for (AnalysisFinding finding : findings) {
            if (finding.getFileId() != null) {
                findingsByFile.computeIfAbsent(finding.getFileId(), ignored -> new ArrayList<>())
                        .add(finding);
            }
        }
        findingsByFile.values().forEach(list -> list.sort(Comparator
                .comparing(AnalysisFinding::getLineStart,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AnalysisFinding::getRuleId)));
        Map<String, ProjectFile> classes = classIndex(files);
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        for (ProjectFile file : files) {
            if (file.getFileType() == FileType.TEST || !isActiveSource(file)) {
                continue;
            }
            List<AnalysisFinding> fileFindings = findingsByFile.getOrDefault(file.getId(), List.of());
            switch (file.getFileType()) {
                case JAVA -> {
                    addAction(file, fileFindings, candidates);
                    addForm(file, fileFindings, candidates);
                    addComponent(file, fileFindings, candidates);
                    addData(file, fileFindings, candidates);
                    addTransaction(file, fileFindings, candidates);
                    addMessageFlow(file, fileFindings, candidates);
                    addValidation(file, fileFindings, candidates);
                    addJakarta(file, fileFindings, candidates);
                }
                case JSP -> addView(file, fileFindings, candidates);
                case DICON -> {
                    addConfiguration(file, fileFindings, candidates);
                    addDiconComponents(file, fileFindings, candidates, classes);
                }
                case XML -> {
                    addConfiguration(file, fileFindings, candidates);
                    addFormBeans(file, fileFindings, candidates, classes);
                }
                case SQL -> addSqlData(file, fileFindings, candidates);
                default -> {
                }
            }
        }

        addMessageResources(relations, fileById, candidates);
        applyRiskEvidence(files, findingsByFile, candidates);
        List<ItemDraft> items = candidates.values().stream()
                .map(Candidate::toDraft)
                .sorted(Comparator.comparing(ItemDraft::title).thenComparing(ItemDraft::key))
                .toList();
        Map<UUID, List<Candidate>> candidatesByFile = new LinkedHashMap<>();
        for (Candidate candidate : candidates.values()) {
            if (candidate.primaryFileId != null) {
                candidatesByFile.computeIfAbsent(candidate.primaryFileId, ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }
        List<DependencyDraft> dependencies = dependencies(relations, fileById, candidatesByFile);
        return new Draft(sourceProfile(technologies), items, dependencies);
    }

    public static int priority(MigrationItemStatus status, MigrationRisk risk) {
        if (status == MigrationItemStatus.BLOCKED) {
            return 1;
        }
        if (status == MigrationItemStatus.REVIEW_REQUIRED && risk == MigrationRisk.HIGH) {
            return 1;
        }
        if (status == MigrationItemStatus.REVIEW_REQUIRED || risk == MigrationRisk.HIGH) {
            return 2;
        }
        return 3;
    }

    private void addAction(ProjectFile file, List<AnalysisFinding> findings,
                           Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, ACTION_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "ACTION:" + file.getRelativePath(),
                MigrationItemType.ACTION_CONTROLLER, MigrationTarget.SPRING_MVC,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.READY, "ACTION_EVIDENCE");
        candidate.addFindings(triggers);
    }

    private void addForm(ProjectFile file, List<AnalysisFinding> findings,
                         Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, FORM_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "FORM:" + file.getRelativePath(),
                MigrationItemType.FORM_MODEL, MigrationTarget.SPRING_MVC,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.LOW, MigrationItemStatus.READY, "FORM_BINDING");
        candidate.addFindings(triggers);
    }

    private void addComponent(ProjectFile file, List<AnalysisFinding> findings,
                              Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, COMPONENT_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "COMPONENT:" + file.getRelativePath(),
                MigrationItemType.COMPONENT_BEAN, MigrationTarget.SPRING_DI,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.READY, "DI_CONFIGURATION");
        candidate.addFindings(triggers);
    }

    private void addData(ProjectFile file, List<AnalysisFinding> findings,
                         Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, DATA_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "DATA:" + file.getRelativePath(),
                MigrationItemType.DATA_ACCESS, MigrationTarget.SPRING_JDBC,
                file.getFileName(), file, triggers, MigrationAutomation.MANUAL,
                MigrationRisk.HIGH, MigrationItemStatus.REVIEW_REQUIRED, "DATA_SEMANTICS");
        candidate.addFindings(triggers);
    }

    private void addSqlData(ProjectFile file, List<AnalysisFinding> findings,
                            Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, Set.of("SQL-STATEMENT-001"));
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "DATA:" + file.getRelativePath(),
                MigrationItemType.DATA_ACCESS, MigrationTarget.SPRING_JDBC,
                file.getFileName(), file, triggers, MigrationAutomation.MANUAL,
                MigrationRisk.HIGH, MigrationItemStatus.REVIEW_REQUIRED, "DATA_SEMANTICS");
        candidate.addFindings(triggers);
    }

    private void addMessageFlow(ProjectFile file, List<AnalysisFinding> findings,
                                Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, MESSAGE_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "MESSAGE_FLOW:" + file.getRelativePath(),
                MigrationItemType.MESSAGE_FLOW, MigrationTarget.SPRING_MVC,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.HIGH, MigrationItemStatus.REVIEW_REQUIRED, "MESSAGE_LIFECYCLE");
        candidate.addFindings(triggers);
    }

    private void addTransaction(ProjectFile file, List<AnalysisFinding> findings,
                                Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, TRANSACTION_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "TRANSACTION:" + file.getRelativePath(),
                MigrationItemType.TRANSACTION, MigrationTarget.SPRING_JDBC,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.REVIEW_REQUIRED, "TRANSACTION_SEMANTICS");
        candidate.addFindings(triggers);
    }

    private void addValidation(ProjectFile file, List<AnalysisFinding> findings,
                               Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, VALIDATION_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "VALIDATION:" + file.getRelativePath(),
                MigrationItemType.VALIDATION, MigrationTarget.SPRING_VALIDATION,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.READY, "VALIDATION_RULES");
        candidate.addFindings(triggers);
    }

    private void addJakarta(ProjectFile file, List<AnalysisFinding> findings,
                            Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = matching(findings, JAKARTA_RULES);
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "JAKARTA:" + file.getRelativePath(),
                MigrationItemType.JAKARTA_NAMESPACE, MigrationTarget.JAKARTA_EE,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.READY, "JAKARTA_NAMESPACE");
        candidate.addFindings(triggers);
    }

    private void addView(ProjectFile file, List<AnalysisFinding> findings,
                         Map<String, Candidate> candidates) {
        List<AnalysisFinding> pageFindings = findings.stream()
                .filter(finding -> finding.getRuleId().startsWith("JSP-"))
                .toList();
        Candidate candidate = candidate(candidates, "VIEW:" + file.getRelativePath(),
                MigrationItemType.JSP_VIEW, MigrationTarget.KEEP_JSP,
                file.getFileName(), file, pageFindings, MigrationAutomation.ASSISTED,
                MigrationRisk.LOW, MigrationItemStatus.READY, "VIEW_RETAINED");
        candidate.addFindings(pageFindings);
        boolean customTags = pageFindings.stream()
                .anyMatch(finding -> CUSTOM_TAG_RULES.contains(finding.getRuleId()));
        if (customTags) {
            candidate.requireReview(MigrationRisk.MEDIUM, "CUSTOM_TAGS");
        }
        boolean strutsTags = pageFindings.stream()
                .anyMatch(finding -> finding.getRuleId().equals("JSP-STRUTS-TAGLIB-001")
                        || finding.getRuleId().equals("JSP-STRUTS-MESSAGE-001")
                        || finding.getRuleId().equals("JSP-STRUTS-MESSAGE-DISPLAY-001")
                        || finding.getRuleId().equals("JSP-FORM-ACTION-001"));
        if (strutsTags && candidate.risk == MigrationRisk.LOW) {
            candidate.risk = MigrationRisk.MEDIUM;
        }
    }

    private void addConfiguration(ProjectFile file, List<AnalysisFinding> findings,
                                  Map<String, Candidate> candidates) {
        List<AnalysisFinding> triggers = findings.stream()
                .filter(finding -> finding.getRuleId().startsWith("WEBXML-")
                        || finding.getRuleId().startsWith("STRUTS-CONFIG-")
                        || finding.getRuleId().startsWith("DICON-"))
                .toList();
        if (triggers.isEmpty()) {
            return;
        }
        Candidate candidate = candidate(candidates, "CONFIG:" + file.getRelativePath(),
                MigrationItemType.CONFIGURATION, MigrationTarget.SPRING_BOOT_CONFIG,
                file.getFileName(), file, triggers, MigrationAutomation.ASSISTED,
                MigrationRisk.MEDIUM, MigrationItemStatus.READY, "CONFIGURATION_MAPPING");
        candidate.addFindings(triggers);
        boolean aop = triggers.stream()
                .anyMatch(finding -> finding.getRuleId().equals("DICON-ASPECT-001")
                        || finding.getRuleId().equals("DICON-INTERCEPTOR-001"));
        if (aop) {
            candidate.requireReview(MigrationRisk.HIGH, "AOP_CONFIGURATION");
        }
    }

    private void addDiconComponents(ProjectFile file, List<AnalysisFinding> findings,
                                    Map<String, Candidate> candidates,
                                    Map<String, ProjectFile> classes) {
        for (AnalysisFinding finding : findings) {
            if (!finding.getRuleId().equals("DICON-COMPONENT-001")) {
                continue;
            }
            JsonNode metadata = metadata(finding);
            String className = metadata.path("class").asText("");
            String componentName = metadata.path("component").asText("");
            ProjectFile source = className.isBlank() ? null : classes.get(className);
            if (source == null) {
                String key = "COMPONENT:" + (className.isBlank() ? file.getRelativePath() + "#" + componentName
                        : className);
                Candidate candidate = candidate(candidates, key,
                        MigrationItemType.COMPONENT_BEAN, MigrationTarget.SPRING_DI,
                        className.isBlank() ? componentName : className, file,
                        List.of(finding), MigrationAutomation.MANUAL, MigrationRisk.HIGH,
                        MigrationItemStatus.REVIEW_REQUIRED, "SOURCE_NOT_FOUND");
                candidate.addFindings(List.of(finding));
            } else {
                Candidate candidate = candidate(candidates, "COMPONENT:" + source.getRelativePath(),
                        MigrationItemType.COMPONENT_BEAN, MigrationTarget.SPRING_DI,
                        source.getFileName(), source, List.of(finding),
                        MigrationAutomation.ASSISTED, MigrationRisk.MEDIUM,
                        MigrationItemStatus.READY, "DI_CONFIGURATION");
                candidate.addFindings(List.of(finding));
            }
        }
    }

    private void addFormBeans(ProjectFile file, List<AnalysisFinding> findings,
                              Map<String, Candidate> candidates,
                              Map<String, ProjectFile> classes) {
        for (AnalysisFinding finding : findings) {
            if (!finding.getRuleId().equals("STRUTS-CONFIG-FORM-001")) {
                continue;
            }
            String type = metadata(finding).path("type").asText("");
            ProjectFile source = type.isBlank() ? null : classes.get(type);
            if (source == null) {
                continue;
            }
            Candidate candidate = candidate(candidates, "FORM:" + source.getRelativePath(),
                    MigrationItemType.FORM_MODEL, MigrationTarget.SPRING_MVC,
                    source.getFileName(), source, List.of(finding),
                    MigrationAutomation.ASSISTED, MigrationRisk.LOW,
                    MigrationItemStatus.READY, "FORM_BINDING");
            candidate.addFindings(List.of(finding));
        }
    }

    private void addMessageResources(List<AnalysisRelation> relations,
                                     Map<UUID, ProjectFile> files,
                                     Map<String, Candidate> candidates) {
        for (AnalysisRelation relation : relations) {
            if (relation.getRelationType() != AnalysisRelationType.MESSAGE_PRODUCTION
                    && relation.getRelationType() != AnalysisRelationType.MESSAGE_DISPLAY) {
                continue;
            }
            ProjectFile target = files.get(relation.getTargetFileId());
            if (target == null || target.getFileType() != FileType.PROPERTIES
                    || !isActiveSource(target)) {
                continue;
            }
            Candidate candidate = candidate(candidates, "RESOURCE:" + target.getRelativePath(),
                    MigrationItemType.MESSAGE_RESOURCE, MigrationTarget.SPRING_MESSAGES,
                    target.getFileName(), target, List.of(),
                    MigrationAutomation.ASSISTED, MigrationRisk.LOW,
                    MigrationItemStatus.READY, "MESSAGE_RESOURCE");
        }
    }

    private void applyRiskEvidence(List<ProjectFile> files,
                                   Map<UUID, List<AnalysisFinding>> findingsByFile,
                                   Map<String, Candidate> candidates) {
        Map<UUID, List<Candidate>> byFile = new LinkedHashMap<>();
        for (Candidate candidate : candidates.values()) {
            if (candidate.primaryFileId != null) {
                byFile.computeIfAbsent(candidate.primaryFileId, ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }
        for (ProjectFile file : files) {
            if (file.getFileType() == FileType.TEST || !isActiveSource(file)) {
                continue;
            }
            List<AnalysisFinding> fileFindings = findingsByFile.getOrDefault(file.getId(), List.of());
            List<AnalysisFinding> unresolved = fileFindings.stream()
                    .filter(finding -> UNRESOLVED_RULES.contains(finding.getRuleId())
                            || CUSTOM_FRAMEWORK_RULES.contains(finding.getRuleId()))
                    .toList();
            List<AnalysisFinding> parseFailures = fileFindings.stream()
                    .filter(finding -> finding.getCertainty() == Certainty.PARSE_FAILED)
                    .toList();
            if (!parseFailures.isEmpty()) {
                Candidate review = candidate(candidates, "REVIEW:" + file.getRelativePath(),
                        MigrationItemType.REVIEW, MigrationTarget.MANUAL_REVIEW,
                        file.getFileName(), file, parseFailures,
                        MigrationAutomation.MANUAL, MigrationRisk.HIGH,
                        MigrationItemStatus.BLOCKED, "PARSE_FAILURE");
                review.addFindings(parseFailures);
            }
            if (!unresolved.isEmpty()) {
                List<Candidate> owners = byFile.getOrDefault(file.getId(), List.of());
                if (owners.isEmpty()) {
                    Candidate review = candidate(candidates, "REVIEW:" + file.getRelativePath(),
                            MigrationItemType.REVIEW, MigrationTarget.MANUAL_REVIEW,
                            file.getFileName(), file, unresolved,
                            MigrationAutomation.MANUAL, MigrationRisk.HIGH,
                            MigrationItemStatus.REVIEW_REQUIRED, "UNRESOLVED_BEHAVIOR");
                    review.addFindings(unresolved);
                    continue;
                }
                for (Candidate owner : owners) {
                    owner.addFindings(unresolved);
                    if (owner.status != MigrationItemStatus.BLOCKED) {
                        owner.requireReview(MigrationRisk.HIGH, "UNRESOLVED_BEHAVIOR");
                    }
                }
            }
            List<AnalysisFinding> customTagFindings = fileFindings.stream()
                    .filter(finding -> CUSTOM_TAG_RULES.contains(finding.getRuleId()))
                    .toList();
            if (!customTagFindings.isEmpty()) {
                for (Candidate owner : byFile.getOrDefault(file.getId(), List.of())) {
                    if (owner.itemType == MigrationItemType.JSP_VIEW) {
                        owner.addFindings(customTagFindings);
                        owner.requireReview(MigrationRisk.MEDIUM, "CUSTOM_TAGS");
                    }
                }
            }
            if (file.getScanStatus() == dev.stravique.domain.FileScanStatus.PARSE_FAILED
                    && parseFailures.isEmpty()) {
                candidate(candidates, "REVIEW:" + file.getRelativePath(),
                        MigrationItemType.REVIEW, MigrationTarget.MANUAL_REVIEW,
                        file.getFileName(), file, List.of(),
                        MigrationAutomation.MANUAL, MigrationRisk.HIGH,
                        MigrationItemStatus.BLOCKED, "PARSE_FAILURE");
            }
        }
    }

    private List<DependencyDraft> dependencies(List<AnalysisRelation> relations,
                                               Map<UUID, ProjectFile> files,
                                               Map<UUID, List<Candidate>> candidatesByFile) {
        Map<String, DependencyDraft> result = new LinkedHashMap<>();
        for (AnalysisRelation relation : relations) {
            if (!DEPENDENCY_TYPES.contains(relation.getRelationType())) {
                continue;
            }
            ProjectFile sourceFile = files.get(relation.getSourceFileId());
            ProjectFile targetFile = files.get(relation.getTargetFileId());
            if (sourceFile == null || targetFile == null
                    || !isActiveSource(sourceFile) || !isActiveSource(targetFile)) {
                continue;
            }
            Candidate source = select(candidatesByFile.get(sourceFile.getId()),
                    sourceFile, relation.getRelationType(), true);
            Candidate target = select(candidatesByFile.get(targetFile.getId()),
                    targetFile, relation.getRelationType(), false);
            if (source == null || target == null || source.key.equals(target.key)) {
                continue;
            }
            String key = source.key + "|" + target.key + "|" + relation.getRelationType() + "|"
                    + relation.getEvidencePath() + "|" + relation.getLineStart();
            result.putIfAbsent(key, new DependencyDraft(source.key, target.key, relation.getId(),
                    relation.getRelationType(), relation.getEvidencePath(), relation.getLineStart(),
                    relation.getCertainty()));
        }
        return List.copyOf(result.values());
    }

    private Candidate select(List<Candidate> candidates, ProjectFile file,
                             AnalysisRelationType relationType, boolean source) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (source) {
            if (relationType == AnalysisRelationType.MESSAGE_PRODUCTION) {
                Candidate message = byType(candidates, MigrationItemType.MESSAGE_FLOW);
                if (message != null) {
                    return message;
                }
            }
            if (relationType == AnalysisRelationType.DAO_SQL_REFERENCE) {
                Candidate data = byType(candidates, MigrationItemType.DATA_ACCESS);
                if (data != null) {
                    return data;
                }
            }
            if (relationType == AnalysisRelationType.JSP_FORM_TO_ACTION) {
                Candidate view = byType(candidates, MigrationItemType.JSP_VIEW);
                if (view != null) {
                    return view;
                }
            }
            if (file.getFileType() == FileType.DICON || file.getFileType() == FileType.XML) {
                Candidate configuration = byType(candidates, MigrationItemType.CONFIGURATION);
                if (configuration != null) {
                    return configuration;
                }
            }
        } else {
            if (relationType == AnalysisRelationType.MESSAGE_PRODUCTION
                    || relationType == AnalysisRelationType.MESSAGE_DISPLAY) {
                Candidate resource = byType(candidates, MigrationItemType.MESSAGE_RESOURCE);
                if (resource != null) {
                    return resource;
                }
            }
            if (relationType == AnalysisRelationType.JSP_FORM_TO_ACTION) {
                Candidate action = byType(candidates, MigrationItemType.ACTION_CONTROLLER);
                if (action != null) {
                    return action;
                }
            }
        }
        return candidates.stream()
                .min(Comparator.comparing(candidate -> PREFERENCE.getOrDefault(candidate.itemType, 99)))
                .orElse(null);
    }

    private Candidate byType(List<Candidate> candidates, MigrationItemType type) {
        return candidates.stream().filter(candidate -> candidate.itemType == type)
                .findFirst().orElse(null);
    }

    private Candidate candidate(Map<String, Candidate> candidates, String key,
                                MigrationItemType type, MigrationTarget target, String title,
                                ProjectFile file, List<AnalysisFinding> findings,
                                MigrationAutomation automation, MigrationRisk risk,
                                MigrationItemStatus status, String reasonCode) {
        String safeKey = boundedKey(key);
        Candidate candidate = candidates.get(safeKey);
        if (candidate != null) {
            return candidate;
        }
        candidate = new Candidate(safeKey, type, target, boundedText(title, 500), file.getId(),
                findings.isEmpty() ? null : findings.getFirst().getId(), automation, risk, status,
                reasonCode);
        candidates.put(safeKey, candidate);
        return candidate;
    }

    private String boundedKey(String key) {
        if (key.length() <= 500) {
            return key;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return key.substring(0, 460) + "#" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception exception) {
            return key.substring(0, 500);
        }
    }

    private String boundedText(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private List<AnalysisFinding> matching(List<AnalysisFinding> findings, Set<String> rules) {
        return findings.stream().filter(finding -> rules.contains(finding.getRuleId())).toList();
    }

    private JsonNode metadata(AnalysisFinding finding) {
        if (finding.getMetadata() == null || finding.getMetadata().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(finding.getMetadata());
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean isActiveSource(ProjectFile file) {
        return file.getChangeType() != FileChangeType.DELETED
                && file.getScanStatus() != FileScanStatus.SKIPPED;
    }

    private Map<String, ProjectFile> classIndex(List<ProjectFile> files) {
        Map<String, ProjectFile> result = new HashMap<>();
        Map<String, List<ProjectFile>> suffixMatches = new HashMap<>();
        for (ProjectFile file : files) {
            if (file.getFileType() != FileType.JAVA || !isActiveSource(file)) {
                continue;
            }
            String path = file.getRelativePath();
            String withoutSuffix = path.substring(0, path.length() - 5);
            int javaMarker = path.lastIndexOf("/java/");
            if (javaMarker >= 0) {
                String fqcn = withoutSuffix.substring(javaMarker + 6).replace('/', '.');
                result.putIfAbsent(fqcn, file);
            }
            String simple = withoutSuffix.replace('/', '.');
            suffixMatches.computeIfAbsent(simple, ignored -> new ArrayList<>()).add(file);
            suffixMatches.computeIfAbsent(path.substring(path.lastIndexOf('/') + 1),
                    ignored -> new ArrayList<>()).add(file);
        }
        for (Map.Entry<String, List<ProjectFile>> entry : suffixMatches.entrySet()) {
            if (entry.getValue().size() == 1) {
                result.putIfAbsent(entry.getKey(), entry.getValue().getFirst());
            }
        }
        return result;
    }

    private String sourceProfile(List<TechnologyUsage> technologies) {
        Set<String> keys = new LinkedHashSet<>();
        for (TechnologyUsage technology : technologies) {
            keys.add(technology.getTechnologyKey());
        }
        List<String> ordered = SOURCE_PROFILE_ORDER.stream().filter(keys::contains).toList();
        return ordered.isEmpty() ? "UNKNOWN" : String.join(", ", ordered);
    }

    public record Draft(String sourceProfile, List<ItemDraft> items,
                        List<DependencyDraft> dependencies) {
    }

    public record ItemDraft(String key, MigrationItemType type, MigrationTarget target,
                            String title, UUID primaryFileId, UUID primaryFindingId,
                            List<UUID> findingIds, MigrationAutomation automation,
                            MigrationRisk risk, MigrationItemStatus status, String reasonCode) {

        public int priority() {
            return MigrationPlanner.priority(status, risk);
        }
    }

    public record DependencyDraft(String fromKey, String toKey, UUID relationId,
                                  AnalysisRelationType relationType, String evidencePath,
                                  Integer lineStart, Certainty certainty) {
    }

    private static final class Candidate {
        private final String key;
        private final MigrationItemType itemType;
        private final MigrationTarget target;
        private final String title;
        private final UUID primaryFileId;
        private UUID primaryFindingId;
        private final Set<UUID> findingIds = new LinkedHashSet<>();
        private final MigrationAutomation automation;
        private MigrationRisk risk;
        private MigrationItemStatus status;
        private String reasonCode;

        private Candidate(String key, MigrationItemType itemType, MigrationTarget target,
                          String title, UUID primaryFileId, UUID primaryFindingId,
                          MigrationAutomation automation, MigrationRisk risk,
                          MigrationItemStatus status, String reasonCode) {
            this.key = key;
            this.itemType = itemType;
            this.target = target;
            this.title = title;
            this.primaryFileId = primaryFileId;
            this.primaryFindingId = primaryFindingId;
            this.automation = automation;
            this.risk = risk;
            this.status = status;
            this.reasonCode = reasonCode;
        }

        private void addFindings(List<AnalysisFinding> findings) {
            for (AnalysisFinding finding : findings) {
                findingIds.add(finding.getId());
            }
            if (primaryFindingId == null && !findings.isEmpty()) {
                primaryFindingId = findings.getFirst().getId();
            }
        }

        private void requireReview(MigrationRisk newRisk, String newReason) {
            if (newRisk.ordinal() > risk.ordinal()) {
                risk = newRisk;
            }
            if (status != MigrationItemStatus.BLOCKED) {
                status = MigrationItemStatus.REVIEW_REQUIRED;
            }
            if (newReason != null && !newReason.equals(reasonCode)) {
                reasonCode = newReason;
            }
        }

        private ItemDraft toDraft() {
            return new ItemDraft(key, itemType, target, title, primaryFileId, primaryFindingId,
                    List.copyOf(findingIds), automation, risk, status, reasonCode);
        }
    }
}
