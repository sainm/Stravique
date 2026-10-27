package dev.stravique.inventory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.stravique.domain.AnalysisRelation;
import dev.stravique.domain.AnalysisRelationType;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileScanStatus;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CrossFileRelationDiscovery {

    private static final Pattern JSP_FORM = Pattern.compile(
            "(?i)<form\\b[^>]*action\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>"
    );

    private final TextDecoder decoder;

    public CrossFileRelationDiscovery(TextDecoder decoder) {
        this.decoder = decoder;
    }

    public List<AnalysisRelation> discover(Path root, UUID projectId, UUID scanId,
                                           List<ProjectFile> files, String analyzerVersion) {
        Map<String, ProjectFile> filesByPath = new LinkedHashMap<>();
        files.stream().filter(file -> file.getScanStatus() == FileScanStatus.ANALYZED)
                .forEach(file -> filesByPath.put(file.getRelativePath(), file));

        List<JavaSource> javaSources = javaSources(root, filesByPath);
        Map<String, JavaSource> byFqn = new LinkedHashMap<>();
        Map<String, List<JavaSource>> bySimpleName = new HashMap<>();
        for (JavaSource source : javaSources) {
            byFqn.put(source.fqn(), source);
            bySimpleName.computeIfAbsent(source.simpleName(), ignored -> new ArrayList<>()).add(source);
        }

        List<AnalysisRelation> relations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JavaSource source : javaSources) {
            for (ImportDeclaration imported : source.unit().getImports()) {
                JavaSource target = byFqn.get(imported.getNameAsString());
                if (target == null || target.file().getId().equals(source.file().getId())) {
                    continue;
                }
                int line = line(imported.getBegin());
                add(relations, seen, AnalysisRelation.create(
                        projectId, scanId, source.file().getId(), target.file().getId(),
                        source.fqn(), target.fqn(), AnalysisRelationType.JAVA_IMPORT,
                        source.file().getRelativePath(), line, lineText(source.text(), line),
                        "JAVA-PROJECT-IMPORT-001", Certainty.ANALYZED, analyzerVersion
                ));
            }
            for (var type : source.declaration().getExtendedTypes()) {
                addInheritance(relations, seen, projectId, scanId, source, type,
                        resolveType(type.asString(), source, byFqn, bySimpleName), analyzerVersion);
            }
            for (var type : source.declaration().getImplementedTypes()) {
                addInheritance(relations, seen, projectId, scanId, source, type,
                        resolveType(type.asString(), source, byFqn, bySimpleName), analyzerVersion);
            }
        }

        Map<String, JavaSource> strutsActions = parseStrutsRelations(
                root, projectId, scanId, filesByPath, byFqn, relations, seen, analyzerVersion);
        relateJspForms(root, projectId, scanId, filesByPath, strutsActions,
                relations, seen, analyzerVersion);
        return List.copyOf(relations);
    }

    private List<JavaSource> javaSources(Path root, Map<String, ProjectFile> files) {
        List<JavaSource> result = new ArrayList<>();
        for (ProjectFile file : files.values()) {
            if (file.getFileType() != FileType.JAVA && file.getFileType() != FileType.TEST) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(file.getRelativePath()));
                CompilationUnit unit = StaticJavaParser.parse(decoded.text());
                ClassOrInterfaceDeclaration declaration = unit.getTypes().stream()
                        .filter(ClassOrInterfaceDeclaration.class::isInstance)
                        .map(ClassOrInterfaceDeclaration.class::cast)
                        .findFirst().orElse(null);
                if (declaration == null) {
                    continue;
                }
                String packageName = unit.getPackageDeclaration()
                        .map(packageDeclaration -> packageDeclaration.getNameAsString())
                        .orElse("");
                String fqn = packageName.isBlank()
                        ? declaration.getNameAsString()
                        : packageName + "." + declaration.getNameAsString();
                Map<String, String> imports = new LinkedHashMap<>();
                for (ImportDeclaration imported : unit.getImports()) {
                    if (!imported.isAsterisk()) {
                        imports.put(imported.getName().getIdentifier(), imported.getNameAsString());
                    }
                }
                result.add(new JavaSource(file, decoded.text(), unit, declaration, fqn,
                        packageName, imports));
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
        return result;
    }

    private JavaSource resolveType(String typeName, JavaSource source,
                                   Map<String, JavaSource> byFqn,
                                   Map<String, List<JavaSource>> bySimpleName) {
        JavaSource imported = byFqn.get(source.imports().getOrDefault(typeName, ""));
        if (imported != null) {
            return imported;
        }
        JavaSource exact = byFqn.get(typeName);
        if (exact != null) {
            return exact;
        }
        String samePackage = source.packageName().isBlank()
                ? typeName : source.packageName() + "." + typeName;
        JavaSource local = byFqn.get(samePackage);
        if (local != null) {
            return local;
        }
        List<JavaSource> simpleMatches = bySimpleName.getOrDefault(typeName, List.of());
        return simpleMatches.size() == 1 ? simpleMatches.getFirst() : null;
    }

    private void addInheritance(List<AnalysisRelation> relations, Set<String> seen,
                                UUID projectId, UUID scanId, JavaSource source,
                                ClassOrInterfaceType type, JavaSource target,
                                String analyzerVersion) {
        if (target == null || target.file().getId().equals(source.file().getId())) {
            return;
        }
        int line = line(type.getBegin());
        add(relations, seen, AnalysisRelation.create(
                projectId, scanId, source.file().getId(), target.file().getId(),
                source.fqn(), target.fqn(), AnalysisRelationType.JAVA_INHERITANCE,
                source.file().getRelativePath(), line, lineText(source.text(), line),
                "JAVA-PROJECT-INHERITANCE-001", Certainty.ANALYZED, analyzerVersion
        ));
    }

    private Map<String, JavaSource> parseStrutsRelations(
            Path root, UUID projectId, UUID scanId, Map<String, ProjectFile> files,
            Map<String, JavaSource> byFqn, List<AnalysisRelation> relations, Set<String> seen,
            String analyzerVersion) {
        Map<String, JavaSource> actionPaths = new LinkedHashMap<>();
        for (ProjectFile file : files.values()) {
            if (file.getFileType() != FileType.XML
                    || !file.getRelativePath().endsWith("struts-config.xml")) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(file.getRelativePath()));
                Document document = SecureXml.parse(decoded.text());
                Element mappings = directChild(document.getDocumentElement(), "action-mappings");
                if (mappings == null) {
                    continue;
                }
                for (Element action : directChildren(mappings, "action")) {
                    String actionPath = action.getAttribute("path");
                    String actionType = action.getAttribute("type");
                    JavaSource source = byFqn.get(actionType);
                    if (source == null || actionPath.isBlank()) {
                        continue;
                    }
                    actionPaths.put(actionPath, source);
                    for (Element forward : directChildren(action, "forward")) {
                        String path = forward.getAttribute("path");
                        ProjectFile jsp = uniquePathSuffix(files, "/" + stripLeadingSlash(path), FileType.JSP);
                        if (jsp == null) {
                            continue;
                        }
                        int line = findLine(decoded.text(), path);
                        add(relations, seen, AnalysisRelation.create(
                                projectId, scanId, source.file().getId(), jsp.getId(),
                                source.fqn(), jsp.getRelativePath(),
                                AnalysisRelationType.STRUTS_ACTION_TO_JSP,
                                file.getRelativePath(), line, lineText(decoded.text(), line),
                                "STRUTS-CONFIG-FORWARD-001", Certainty.ANALYZED, analyzerVersion
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
        return actionPaths;
    }

    private void relateJspForms(Path root, UUID projectId, UUID scanId,
                                Map<String, ProjectFile> files,
                                Map<String, JavaSource> actionPaths,
                                List<AnalysisRelation> relations, Set<String> seen,
                                String analyzerVersion) {
        for (ProjectFile jsp : files.values()) {
            if (jsp.getFileType() != FileType.JSP) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(jsp.getRelativePath()));
                List<String> lines = decoded.text().lines().toList();
                for (int index = 0; index < lines.size(); index++) {
                    Matcher matcher = JSP_FORM.matcher(lines.get(index));
                    while (matcher.find()) {
                        JavaSource action = actionPaths.get(matcher.group(1));
                        if (action == null) {
                            continue;
                        }
                        add(relations, seen, AnalysisRelation.create(
                                projectId, scanId, jsp.getId(), action.file().getId(),
                                jsp.getRelativePath(), action.fqn(),
                                AnalysisRelationType.JSP_FORM_TO_ACTION,
                                jsp.getRelativePath(), index + 1, lines.get(index).strip(),
                                "JSP-FORM-TO-ACTION-001", Certainty.INFERRED, analyzerVersion
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
    }

    private ProjectFile uniquePathSuffix(Map<String, ProjectFile> files, String suffix,
                                         FileType type) {
        List<ProjectFile> matches = files.values().stream()
                .filter(file -> file.getFileType() == type)
                .filter(file -> ("/" + file.getRelativePath()).endsWith(suffix))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private void add(List<AnalysisRelation> relations, Set<String> seen,
                     AnalysisRelation relation) {
        String key = relation.getSourceFileId() + ":" + relation.getTargetFileId() + ":"
                + relation.getRelationType() + ":" + relation.getEvidencePath() + ":"
                + relation.getLineStart();
        if (seen.add(key)) {
            relations.add(relation);
        }
    }

    private Element directChild(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && matches(element, localName)) {
                return element;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && matches(element, localName)) {
                result.add(element);
            }
        }
        return result;
    }

    private boolean matches(Element element, String localName) {
        String name = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
        return localName.equals(name);
    }

    private int findLine(String text, String needle) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(needle)) {
                return index + 1;
            }
        }
        return 1;
    }

    private String lineText(String text, int lineNumber) {
        String[] lines = text.split("\\R", -1);
        if (lineNumber <= 0 || lineNumber > lines.length) {
            return "";
        }
        String value = lines[lineNumber - 1].strip();
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String stripLeadingSlash(String value) {
        return value == null ? "" : value.replaceAll("^/+", "");
    }

    private int line(java.util.Optional<com.github.javaparser.Position> position) {
        return position.map(value -> value.line).orElse(1);
    }

    private record JavaSource(
            ProjectFile file,
            String text,
            CompilationUnit unit,
            ClassOrInterfaceDeclaration declaration,
            String fqn,
            String packageName,
            Map<String, String> imports
    ) {
        private String simpleName() {
            return declaration.getNameAsString();
        }
    }
}
