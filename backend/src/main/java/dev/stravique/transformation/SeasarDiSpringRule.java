package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SeasarDiSpringRule implements TransformationRule {

    private static final String RULE_ID = "SEASAR-DI-SPRING-DI";
    private static final String RULE_VERSION = "1.1.0";
    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";
    private static final String SPRING_AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String SPRING_SCOPE = "org.springframework.context.annotation.Scope";
    private static final Set<String> SCOPES = Set.of(
            "prototype", "request", "session", "application");
    private static final Set<String> SEASAR_COMPONENT_IMPORTS = new LinkedHashSet<>(List.of(
            "org.seasar.framework.container.annotation.tiger.Component",
            "org.seasar.framework.container.annotation.twig.Component"
    ));
    private static final Set<String> SEASAR_BINDING_IMPORTS = new LinkedHashSet<>(List.of(
            "org.seasar.framework.container.annotation.tiger.Binding",
            "org.seasar.framework.container.annotation.twig.Binding"
    ));

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String ruleVersion() {
        return RULE_VERSION;
    }

    @Override
    public MigrationItemType itemType() {
        return MigrationItemType.COMPONENT_BEAN;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_DI;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.COMPONENT_BEAN
                && item.getTargetKey() == MigrationTarget.SPRING_DI
                && file.getFileType() == FileType.JAVA;
    }

    @Override
    public String propose(String source, String relativePath) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        List<Edit> edits = new ArrayList<>();
        Set<String> componentImports = imports(unit, SEASAR_COMPONENT_IMPORTS);
        Set<String> bindingImports = imports(unit, SEASAR_BINDING_IMPORTS);
        if (componentImports.isEmpty() && bindingImports.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "The source does not import a supported Seasar DI annotation.");
        }
        boolean hasComponentAnnotation = false;
        boolean hasBindingAnnotation = false;
        boolean needsScope = false;
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            String simpleName = simpleName(annotation.getNameAsString());
            if (simpleName.equals("Component")) {
                if (componentImports.isEmpty()) {
                    throw new TransformationUnsupportedException(
                            "@Component is not imported from a supported Seasar package.");
                }
                hasComponentAnnotation = true;
                ComponentReplacement replacement = componentReplacement(annotation);
                if (replacement.scope() != null) {
                    needsScope = true;
                }
                edits.add(new Edit(offsets(source, annotation), replacement.annotation()));
            } else if (simpleName.equals("Binding")) {
                if (bindingImports.isEmpty()) {
                    throw new TransformationUnsupportedException(
                            "@Binding is not imported from a supported Seasar package.");
                }
                if (!(annotation instanceof MarkerAnnotationExpr)) {
                    throw new TransformationUnsupportedException(
                            "@Binding attributes require manual review.");
                }
                hasBindingAnnotation = true;
                edits.add(new Edit(offsets(source, annotation), "@Autowired"));
            }
        }
        if (!hasComponentAnnotation && !hasBindingAnnotation) {
            throw new TransformationUnsupportedException(
                    "The source imports Seasar DI annotations but does not use a supported construct.");
        }
        for (String imported : componentImports) {
            addImportEdit(unit, source, imported, SPRING_COMPONENT, edits);
        }
        for (String imported : bindingImports) {
            addImportEdit(unit, source, imported, SPRING_AUTOWIRED, edits);
        }
        if (needsScope) {
            addScopeImport(unit, source, edits);
        }
        return apply(source, edits);
    }

    private void addScopeImport(CompilationUnit unit, String source, List<Edit> edits) {
        boolean targetPresent = unit.getImports().stream()
                .anyMatch(candidate -> candidate.getNameAsString().equals(SPRING_SCOPE));
        if (targetPresent) {
            return;
        }
        for (ImportDeclaration declaration : unit.getImports()) {
            if (simpleName(declaration.getNameAsString()).equals("Scope")) {
                throw new TransformationUnsupportedException(
                        "A conflicting Scope import requires manual review.");
            }
        }
        ImportDeclaration anchor = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(SPRING_COMPONENT))
                .findFirst()
                .orElseGet(() -> unit.getImports().stream()
                        .filter(candidate -> SEASAR_COMPONENT_IMPORTS.contains(
                                candidate.getNameAsString()))
                        .findFirst()
                        .orElseThrow(() -> new TransformationUnsupportedException(
                                "No component import is available to anchor the Scope import.")));
        int end = offsets(source, anchor)[1];
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        edits.add(new Edit(end, end, newline + "import " + SPRING_SCOPE + ";"));
    }

    private Set<String> imports(CompilationUnit unit, Set<String> supported) {
        Set<String> result = new LinkedHashSet<>();
        for (ImportDeclaration declaration : unit.getImports()) {
            if (supported.contains(declaration.getNameAsString())) {
                result.add(declaration.getNameAsString());
            }
        }
        return result;
    }

    private void addImportEdit(CompilationUnit unit, String source, String imported, String target,
                               List<Edit> edits) {
        ImportDeclaration declaration = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(imported))
                .findFirst()
                .orElseThrow();
        boolean targetPresent = unit.getImports().stream()
                .anyMatch(candidate -> candidate.getNameAsString().equals(target));
        if (targetPresent) {
            edits.add(new Edit(offsets(source, declaration), ""));
        } else {
            edits.add(new Edit(offsets(source, declaration.getName()), target));
        }
    }

    private ComponentReplacement componentReplacement(AnnotationExpr annotation) {
        if (annotation instanceof MarkerAnnotationExpr) {
            if (hasAnnotation(annotation, "Scope")) {
                throw new TransformationUnsupportedException(
                        "@Scope on the component requires manual review.");
            }
            return new ComponentReplacement("@Component", null);
        }
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            throw new TransformationUnsupportedException(
                    "This @Component form requires manual review.");
        }
        String name = null;
        String instance = null;
        for (MemberValuePair pair : normal.getPairs()) {
            String key = pair.getNameAsString();
            if (!(pair.getValue() instanceof StringLiteralExpr literal)) {
                throw new TransformationUnsupportedException(
                        "@Component attributes must be string literals.");
            }
            if (key.equals("name")) {
                name = literal.getValue();
            } else if (key.equals("instance")) {
                instance = literal.getValue();
            } else {
                throw new TransformationUnsupportedException(
                        "@Component attribute " + key + " requires manual review.");
            }
        }
        String scope = null;
        if (instance != null && hasAnnotation(annotation, "Scope")) {
            throw new TransformationUnsupportedException(
                    "@Scope on the component requires manual review.");
        }
        if (instance != null && !instance.equals("singleton")) {
            if (!SCOPES.contains(instance)) {
                throw new TransformationUnsupportedException(
                        "@Component instance " + instance + " requires manual review.");
            }
            scope = instance;
        }
        String component = "@Component";
        if (name != null) {
            String escaped = name.replace("\\", "\\\\").replace("\"", "\\\"");
            component = "@Component(\"" + escaped + "\")";
        }
        String replacement = scope == null ? component
                : component + " @Scope(\"" + scope + "\")";
        return new ComponentReplacement(replacement, scope);
    }

    private boolean hasAnnotation(AnnotationExpr annotation, String simpleName) {
        Node parent = annotation.getParentNode().orElse(null);
        if (parent == null) {
            return false;
        }
        return parent.getChildNodes().stream()
                .filter(AnnotationExpr.class::isInstance)
                .map(AnnotationExpr.class::cast)
                .anyMatch(candidate -> simpleName(candidate.getNameAsString()).equals(simpleName));
    }

    private String apply(String source, List<Edit> edits) {
        List<Edit> sorted = edits.stream()
                .sorted(Comparator.comparingInt(Edit::start))
                .toList();
        int previousEnd = -1;
        for (Edit edit : sorted) {
            if (edit.start() < previousEnd || edit.end() < edit.start()) {
                throw new TransformationUnsupportedException(
                        "Overlapping source edits cannot be applied safely.");
            }
            previousEnd = edit.end();
        }
        String result = source;
        for (int index = sorted.size() - 1; index >= 0; index--) {
            Edit edit = sorted.get(index);
            result = result.substring(0, edit.start()) + edit.replacement() + result.substring(edit.end());
        }
        return result;
    }

    private int[] offsets(String source, Node node) {
        Range range = node.getRange().orElseThrow(() -> new TransformationUnsupportedException(
                "The parser did not provide a source range."));
        int[] starts = lineStarts(source);
        int start = offset(range.begin, starts);
        int end = endOffset(range.end, starts);
        if (start < 0 || end > source.length()) {
            throw new TransformationUnsupportedException(
                    "The parser source range is outside the file.");
        }
        return new int[]{start, end};
    }

    private int[] lineStarts(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                starts.add(index + 1);
            }
        }
        int[] values = new int[starts.size()];
        for (int index = 0; index < starts.size(); index++) {
            values[index] = starts.get(index);
        }
        return values;
    }

    private int offset(Position position, int[] lineStarts) {
        if (position.line < 1 || position.line > lineStarts.length) {
            throw new TransformationUnsupportedException("Invalid parser line position.");
        }
        return lineStarts[position.line - 1] + position.column - 1;
    }

    private int endOffset(Position position, int[] lineStarts) {
        if (position.line < 1 || position.line > lineStarts.length) {
            throw new TransformationUnsupportedException("Invalid parser line position.");
        }
        return lineStarts[position.line - 1] + position.column;
    }

    private String simpleName(String value) {
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private record Edit(int start, int end, String replacement) {

        private Edit(int[] offsets, String replacement) {
            this(offsets[0], offsets[1], replacement);
        }
    }

    private record ComponentReplacement(String annotation, String scope) {
    }
}
