package dev.stravique.inventory;

import dev.stravique.domain.Certainty;
import dev.stravique.domain.TechnologyCategory;
import dev.stravique.domain.TechnologyUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TechEvidenceLedger {

    private final Map<String, TechEvidence> technologies = new LinkedHashMap<>();

    public void recordDependency(String key, String name, String path, Integer line, String ruleId) {
        record(key, name, category(key), EvidenceSource.DEPENDENCY, path, line, ruleId);
    }

    public void recordApi(String key, String name, String path, Integer line, String ruleId) {
        record(key, name, category(key), EvidenceSource.API, path, line, ruleId);
    }

    public void recordConfiguration(String key, String name, String path, Integer line, String ruleId) {
        record(key, name, category(key), EvidenceSource.CONFIGURATION, path, line, ruleId);
    }

    public void recordInferredApi(String key, String name, String path, Integer line, String ruleId) {
        record(key, name, category(key), EvidenceSource.API, path, line, ruleId);
        technologies.get(key).uncertain = true;
    }

    public List<TechnologyUsage> toEntities(UUID projectId, UUID scanId, String analyzerVersion) {
        return technologies.values().stream()
                .sorted(Comparator.comparing(entry -> entry.key))
                .map(entry -> TechnologyUsage.create(
                        projectId,
                        scanId,
                        entry.key,
                        entry.name,
                        entry.category,
                        entry.status(),
                        entry.apiCount,
                        entry.configurationCount,
                        entry.dependencyCount,
                        String.join(",", entry.ruleIds),
                        entry.firstPath,
                        entry.firstLine,
                        analyzerVersion
                ))
                .toList();
    }

    public Set<String> keys() {
        return technologies.keySet();
    }

    private void record(String key, String name, TechnologyCategory category, EvidenceSource source,
                        String path, Integer line, String ruleId) {
        TechEvidence evidence = technologies.computeIfAbsent(key,
                ignored -> new TechEvidence(key, name, category));
        evidence.increment(source);
        evidence.ruleIds.add(ruleId);
        if (evidence.firstPath == null
                || (path != null && path.compareTo(evidence.firstPath) < 0)
                || (path != null && path.equals(evidence.firstPath) && line != null
                && (evidence.firstLine == null || line < evidence.firstLine))) {
            evidence.firstPath = path;
            evidence.firstLine = line;
        }
    }

    private TechnologyCategory category(String key) {
        return switch (key) {
            case "JAVA" -> TechnologyCategory.LANGUAGE;
            case "MAVEN", "GRADLE" -> TechnologyCategory.BUILD;
            case "SASTRUTS", "STRUTS1", "SEASAR2", "S2JDBC", "CUSTOM_FRAMEWORK" ->
                    TechnologyCategory.FRAMEWORK;
            case "SERVLET", "JSP", "JSTL", "CUSTOM_TAGS" -> TechnologyCategory.WEB;
            case "JUNIT" -> TechnologyCategory.TEST;
            case "DB_ACCESS" -> TechnologyCategory.DATABASE;
            default -> TechnologyCategory.FRAMEWORK;
        };
    }

    private enum EvidenceSource {
        DEPENDENCY,
        API,
        CONFIGURATION
    }

    private static final class TechEvidence {
        private final String key;
        private final String name;
        private final TechnologyCategory category;
        private int apiCount;
        private int configurationCount;
        private int dependencyCount;
        private final Set<String> ruleIds = new LinkedHashSet<>();
        private String firstPath;
        private Integer firstLine;
        private boolean uncertain;

        private TechEvidence(String key, String name, TechnologyCategory category) {
            this.key = key;
            this.name = name;
            this.category = category;
        }

        private void increment(EvidenceSource source) {
            switch (source) {
                case API -> apiCount++;
                case CONFIGURATION -> configurationCount++;
                case DEPENDENCY -> dependencyCount++;
            }
        }

        private Certainty status() {
            if (uncertain) {
                return Certainty.REVIEW_REQUIRED;
            }
            if (apiCount > 0 || configurationCount > 0) {
                return Certainty.ANALYZED;
            }
            return Certainty.IDENTIFIED;
        }
    }

    public List<String> names() {
        return new ArrayList<>(technologies.values().stream().map(entry -> entry.name).toList());
    }
}
