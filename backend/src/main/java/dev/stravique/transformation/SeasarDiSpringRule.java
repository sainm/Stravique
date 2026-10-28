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
    private static final String RULE_VERSION = "1.0.0";
    private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";
    private static final String SPRING_AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
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
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            String simpleName = simpleName(annotation.getNameAsString());
            if (simpleName.equals("Component")) {
                if (componentImports.isEmpty()) {
                    throw new TransformationUnsupportedException(
                            "@Component is not imported from a supported Seasar package.");
                }
                hasComponentAnnotation = true;
                edits.add(new Edit(offsets(source, annotation), componentReplacement(annotation)));
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
        return apply(source, edits);
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

    private String componentReplacement(AnnotationExpr annotation) {
        if (annotation instanceof MarkerAnnotationExpr) {
            return "@Component";
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
        if (instance != null && !instance.equals("singleton")) {
            throw new TransformationUnsupportedException(
                    "@Component instance " + instance + " requires manual review.");
        }
        if (name != null) {
            String escaped = name.replace("\\", "\\\\").replace("\"", "\\\"");
            return "@Component(\"" + escaped + "\")";
        }
        return "@Component";
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
}
