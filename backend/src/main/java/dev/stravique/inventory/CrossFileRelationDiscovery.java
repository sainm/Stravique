package dev.stravique.inventory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import org.w3c.dom.NodeList;

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

    private static final Pattern CONFIG_CLASS = Pattern.compile(
            "(?:[a-z_][\\w$]*\\.){2,}[A-Z][\\w$]*(?:\\.[A-Z][\\w$]*)*"
    );
    private static final Pattern ACTION_MESSAGE = Pattern.compile(
            "ActionMessage\\s*\\(\\s*[\"']([^\"']+)[\"']"
    );
    private static final Set<String> MESSAGE_PREFIXES = Set.of("fmt", "bean", "spring");
    private static final Pattern SQL_TABLE = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+([A-Za-z_][A-Za-z0-9_.$]*)"
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
            relateBindings(relations, seen, projectId, scanId, source, byFqn,
                    bySimpleName, analyzerVersion);
        }

        Map<String, JavaSource> strutsActions = parseStrutsRelations(
                root, projectId, scanId, filesByPath, byFqn, relations, seen, analyzerVersion);
        relateJspForms(root, projectId, scanId, filesByPath, strutsActions,
                relations, seen, analyzerVersion);
        relateDiconInjection(root, projectId, scanId, filesByPath, byFqn,
                relations, seen, analyzerVersion);
        relateConfigurationClasses(root, projectId, scanId, filesByPath, byFqn,
                relations, seen, analyzerVersion);
        relateSaStrutsReturns(root, projectId, scanId, filesByPath, javaSources,
                relations, seen, analyzerVersion);
        relateDaoSql(root, projectId, scanId, filesByPath, javaSources,
                relations, seen, analyzerVersion);
        relateMessages(root, projectId, scanId, filesByPath, javaSources,
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

    private void relateBindings(List<AnalysisRelation> relations, Set<String> seen,
                                UUID projectId, UUID scanId, JavaSource source,
                                Map<String, JavaSource> byFqn,
                                Map<String, List<JavaSource>> bySimpleName,
                                String analyzerVersion) {
        for (var field : source.declaration().getFields()) {
            if (field.getAnnotations().stream().noneMatch(annotation ->
                    simpleName(annotation.getNameAsString()).equals("Binding"))) {
                continue;
            }
            JavaSource target = resolveType(field.getElementType().asString(), source,
                    byFqn, bySimpleName);
            if (target == null || target.file().getId().equals(source.file().getId())) {
                continue;
            }
            int line = line(field.getBegin());
            add(relations, seen, AnalysisRelation.create(
                    projectId, scanId, source.file().getId(), target.file().getId(),
                    source.fqn(), target.fqn(), AnalysisRelationType.SEASAR_BINDING,
                    source.file().getRelativePath(), line, lineText(source.text(), line),
                    "JAVA-SEASAR-BINDING-RELATION-001", Certainty.ANALYZED, analyzerVersion
            ));
        }
    }

    private void relateDiconInjection(Path root, UUID projectId, UUID scanId,
                                      Map<String, ProjectFile> files,
                                      Map<String, JavaSource> byFqn,
                                      List<AnalysisRelation> relations, Set<String> seen,
                                      String analyzerVersion) {
        List<DiconFile> diconFiles = new ArrayList<>();
        Map<String, JavaSource> componentsByName = new LinkedHashMap<>();
        for (ProjectFile file : files.values()) {
            if (file.getFileType() != FileType.DICON) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(file.getRelativePath()));
                Document document = SecureXml.parse(decoded.text());
                diconFiles.add(new DiconFile(file, decoded.text(), document));
                NodeList components = document.getElementsByTagName("component");
                for (int index = 0; index < components.getLength(); index++) {
                    Element component = (Element) components.item(index);
                    String name = component.getAttribute("name");
                    JavaSource source = byFqn.get(component.getAttribute("class"));
                    if (!name.isBlank() && source != null) {
                        componentsByName.put(name, source);
                    }
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }

        for (DiconFile dicon : diconFiles) {
            NodeList properties = dicon.document().getElementsByTagName("property");
            for (int index = 0; index < properties.getLength(); index++) {
                Element property = (Element) properties.item(index);
                Element owner = ancestor(property, "component");
                if (owner == null) {
                    continue;
                }
                JavaSource source = byFqn.get(owner.getAttribute("class"));
                if (source == null) {
                    String ownerName = owner.getAttribute("name");
                    source = componentsByName.get(ownerName);
                }
                JavaSource target = null;
                Element nested = directChild(property, "component");
                if (nested != null) {
                    target = byFqn.get(nested.getAttribute("class"));
                }
                if (target == null) {
                    target = componentsByName.get(property.getTextContent().trim());
                }
                if (source == null || target == null
                        || source.file().getId().equals(target.file().getId())) {
                    continue;
                }
                String propertyName = property.getAttribute("name");
                String marker = propertyName.isBlank() ? "<property" : propertyName;
                int line = findLine(dicon.text(), marker);
                add(relations, seen, AnalysisRelation.create(
                        projectId, scanId, dicon.file().getId(), target.file().getId(),
                        source.fqn() + "#" + propertyName, target.fqn(),
                        AnalysisRelationType.SEASAR_DICON_INJECTION,
                        dicon.file().getRelativePath(), line, lineText(dicon.text(), line),
                        "DICON-INJECTION-RELATION-001", Certainty.ANALYZED, analyzerVersion
                ));
            }
        }
    }

    private void relateConfigurationClasses(Path root, UUID projectId, UUID scanId,
                                            Map<String, ProjectFile> files,
                                            Map<String, JavaSource> byFqn,
                                            List<AnalysisRelation> relations, Set<String> seen,
                                            String analyzerVersion) {
        for (ProjectFile file : files.values()) {
            if (file.getFileType() != FileType.XML
                    && file.getFileType() != FileType.PROPERTIES
                    && file.getFileType() != FileType.YAML) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(file.getRelativePath()));
                List<String> lines = decoded.text().lines().toList();
                for (int index = 0; index < lines.size(); index++) {
                    Matcher matcher = CONFIG_CLASS.matcher(lines.get(index));
                    while (matcher.find()) {
                        JavaSource target = byFqn.get(matcher.group());
                        if (target == null || target.file().getId().equals(file.getId())) {
                            continue;
                        }
                        add(relations, seen, AnalysisRelation.create(
                                projectId, scanId, file.getId(), target.file().getId(),
                                file.getRelativePath(), target.fqn(),
                                AnalysisRelationType.CONFIG_CLASS_REFERENCE,
                                file.getRelativePath(), index + 1, lines.get(index).strip(),
                                "CONFIG-PROJECT-CLASS-REF-001", Certainty.INFERRED,
                                analyzerVersion
                        ));
                    }
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
    }

    private void relateMessages(Path root, UUID projectId, UUID scanId,
                                Map<String, ProjectFile> files,
                                List<JavaSource> javaSources,
                                List<AnalysisRelation> relations, Set<String> seen,
                                String analyzerVersion) {
        Map<String, List<ProjectFile>> keyFiles = new LinkedHashMap<>();
        for (ProjectFile file : files.values()) {
            if (file.getFileType() != FileType.PROPERTIES) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(file.getRelativePath()));
                for (String line : decoded.text().lines().toList()) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                        continue;
                    }
                    int separator = firstSeparator(trimmed);
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    keyFiles.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file);
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }

        for (JavaSource source : javaSources) {
            Matcher matcher = ACTION_MESSAGE.matcher(source.text());
            while (matcher.find()) {
                ProjectFile messageFile = uniqueTarget(keyFiles.get(matcher.group(1)));
                if (messageFile == null) {
                    continue;
                }
                int line = lineAtOffset(source.text(), matcher.start());
                add(relations, seen, AnalysisRelation.create(
                        projectId, scanId, source.file().getId(), messageFile.getId(),
                        source.fqn(), matcher.group(1),
                        AnalysisRelationType.MESSAGE_PRODUCTION,
                        source.file().getRelativePath(), line, lineText(source.text(), line),
                        "JAVA-MESSAGE-PRODUCTION-001", Certainty.ANALYZED, analyzerVersion
                ));
            }
        }

        for (ProjectFile jsp : files.values()) {
            if (jsp.getFileType() != FileType.JSP) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(jsp.getRelativePath()));
                JspTagScanner.Document document = JspTagScanner.scan(decoded.text());
                Map<String, JspTagScanner.Directive> taglibs = document.taglibsByPrefix();
                List<String> lines = decoded.text().lines().toList();
                for (JspTagScanner.Tag tag : document.tags()) {
                    if (tag.closing() || !tag.name().equalsIgnoreCase("message")) {
                        continue;
                    }
                    String prefix = tag.prefix();
                    String uri = taglibUri(taglibs, prefix);
                    if (!MESSAGE_PREFIXES.contains(prefix) && !isStrutsTaglib(uri)
                            && !uri.toLowerCase(java.util.Locale.ROOT).contains("jstl")) {
                        continue;
                    }
                    String key = tag.attribute("key").strip();
                    ProjectFile messageFile = uniqueTarget(keyFiles.get(key));
                    if (messageFile == null) {
                        continue;
                    }
                    String lineText = tag.line() <= lines.size()
                            ? lines.get(tag.line() - 1).strip() : "";
                    add(relations, seen, AnalysisRelation.create(
                            projectId, scanId, jsp.getId(), messageFile.getId(),
                            jsp.getRelativePath(), key,
                            AnalysisRelationType.MESSAGE_DISPLAY,
                            jsp.getRelativePath(), tag.line(), lineText,
                            "JSP-MESSAGE-DISPLAY-001", Certainty.ANALYZED, analyzerVersion
                    ));
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
    }

    private void relateSaStrutsReturns(Path root, UUID projectId, UUID scanId,
                                       Map<String, ProjectFile> files,
                                       List<JavaSource> javaSources,
                                       List<AnalysisRelation> relations, Set<String> seen,
                                       String analyzerVersion) {
        for (JavaSource source : javaSources) {
            if (!source.text().contains("org.seasar.struts")) {
                continue;
            }
            for (StringLiteralExpr literal : source.unit().findAll(StringLiteralExpr.class)) {
                String value = literal.getValue();
                if (!value.endsWith(".jsp")) {
                    continue;
                }
                ProjectFile jsp = uniquePathSuffix(files, "/" + stripLeadingSlash(value), FileType.JSP);
                if (jsp == null) {
                    continue;
                }
                int line = line(literal.getBegin());
                add(relations, seen, AnalysisRelation.create(
                        projectId, scanId, source.file().getId(), jsp.getId(),
                        source.fqn(), jsp.getRelativePath(),
                        AnalysisRelationType.SASTRUTS_ACTION_TO_JSP,
                        source.file().getRelativePath(), line, lineText(source.text(), line),
                        "JAVA-SAST-JSP-RETURN-001", Certainty.INFERRED, analyzerVersion
                ));
            }
        }
    }

    private void relateDaoSql(Path root, UUID projectId, UUID scanId,
                              Map<String, ProjectFile> files,
                              List<JavaSource> javaSources,
                              List<AnalysisRelation> relations, Set<String> seen,
                              String analyzerVersion) {
        Map<String, List<ProjectFile>> sqlFilesByTable = new LinkedHashMap<>();
        for (ProjectFile sqlFile : files.values()) {
            if (sqlFile.getFileType() != FileType.SQL) {
                continue;
            }
            try {
                TextDecoder.DecodedSource decoded = decoder.decode(root.resolve(sqlFile.getRelativePath()));
                for (String table : tables(decoded.text())) {
                    sqlFilesByTable.computeIfAbsent(table, ignored -> new ArrayList<>()).add(sqlFile);
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }

        for (JavaSource source : javaSources) {
            if (!source.text().contains("org.seasar.extension.jdbc")) {
                continue;
            }
            for (StringLiteralExpr literal : source.unit().findAll(StringLiteralExpr.class)) {
                String sql = literal.getValue();
                if (!looksLikeSql(sql)) {
                    continue;
                }
                int line = line(literal.getBegin());
                for (String table : tables(sql)) {
                    ProjectFile sqlFile = uniqueTarget(sqlFilesByTable.get(table));
                    if (sqlFile == null || sqlFile.getId().equals(source.file().getId())) {
                        continue;
                    }
                    add(relations, seen, AnalysisRelation.create(
                            projectId, scanId, source.file().getId(), sqlFile.getId(),
                            source.fqn(), sqlFile.getRelativePath(),
                            AnalysisRelationType.DAO_SQL_REFERENCE,
                            source.file().getRelativePath(), line, lineText(source.text(), line),
                            "JAVA-S2JDBC-SQL-TABLE-001", Certainty.INFERRED, analyzerVersion
                    ));
                }
            }
        }
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
                JspTagScanner.Document document = JspTagScanner.scan(decoded.text());
                Map<String, JspTagScanner.Directive> taglibs = document.taglibsByPrefix();
                List<String> lines = decoded.text().lines().toList();
                for (JspTagScanner.Tag tag : document.tags()) {
                    if (tag.closing() || !tag.name().equalsIgnoreCase("form")) {
                        continue;
                    }
                    if (!tag.prefix().isEmpty()
                            && !isStrutsTaglib(taglibUri(taglibs, tag.prefix()))) {
                        continue;
                    }
                    String actionPath = normalizeActionPath(tag.attribute("action"));
                    if (actionPath.isEmpty()) {
                        continue;
                    }
                    JavaSource action = actionPaths.get(actionPath);
                    if (action == null) {
                        continue;
                    }
                    String lineText = tag.line() <= lines.size()
                            ? lines.get(tag.line() - 1).strip() : "";
                    add(relations, seen, AnalysisRelation.create(
                            projectId, scanId, jsp.getId(), action.file().getId(),
                            jsp.getRelativePath(), action.fqn(),
                            AnalysisRelationType.JSP_FORM_TO_ACTION,
                            jsp.getRelativePath(), tag.line(), lineText,
                            "JSP-FORM-TO-ACTION-001", Certainty.INFERRED, analyzerVersion
                    ));
                }
            } catch (Exception ignored) {
                // Parse failures are persisted by the per-file analyzer.
            }
        }
    }

    private String taglibUri(Map<String, JspTagScanner.Directive> taglibs, String prefix) {
        JspTagScanner.Directive directive = taglibs.get(prefix);
        return directive == null ? "" : directive.attribute("uri");
    }

    private boolean isStrutsTaglib(String uri) {
        String lower = uri == null ? "" : uri.strip().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://struts.apache.org") || lower.startsWith("https://struts.apache.org")
                || lower.startsWith("http://jakarta.apache.org/struts")
                || lower.startsWith("https://jakarta.apache.org/struts");
    }

    private String normalizeActionPath(String action) {
        String value = action == null ? "" : action.strip();
        if (value.contains("${") || value.contains("#{") || !value.startsWith("/")) {
            return "";
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        return value;
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

    private Element ancestor(Element element, String localName) {
        Node node = element.getParentNode();
        while (node != null) {
            if (node instanceof Element parent && matches(parent, localName)) {
                return parent;
            }
            node = node.getParentNode();
        }
        return null;
    }

    private ProjectFile uniqueTarget(List<ProjectFile> files) {
        if (files == null) {
            return null;
        }
        List<ProjectFile> unique = files.stream().distinct().toList();
        return unique.size() == 1 ? unique.getFirst() : null;
    }

    private int firstSeparator(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '=' || character == ':' || Character.isWhitespace(character)) {
                return index;
            }
        }
        return -1;
    }

    private int lineAtOffset(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String simpleName(String value) {
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private List<String> tables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = SQL_TABLE.matcher(sql == null ? "" : sql);
        while (matcher.find()) {
            tables.add(matcher.group(1).replace("`", "").replace("\"", "")
                    .toLowerCase(java.util.Locale.ROOT));
        }
        return List.copyOf(tables);
    }

    private boolean looksLikeSql(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("select ") || normalized.startsWith("update ")
                || normalized.startsWith("insert ") || normalized.startsWith("delete ")
                || normalized.startsWith("with ") || normalized.startsWith("merge ");
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

    private record DiconFile(ProjectFile file, String text, Document document) {
    }
}
