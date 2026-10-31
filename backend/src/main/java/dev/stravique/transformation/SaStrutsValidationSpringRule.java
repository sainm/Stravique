package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SaStrutsValidationSpringRule implements TransformationRule {

    private static final String RULE_ID = "SASTRUTS-VALIDATION-BEAN";
    private static final String RULE_VERSION = "1.1.0";
    private static final String SOURCE_REQUIRED = "org.seasar.struts.annotation.Required";
    private static final String SOURCE_LENGTH = "org.seasar.struts.annotation.Length";
    private static final String SOURCE_MAX_LENGTH = "org.seasar.struts.annotation.MaxLength";
    private static final String SOURCE_MIN_LENGTH = "org.seasar.struts.annotation.MinLength";
    private static final String TARGET_NOT_NULL = "jakarta.validation.constraints.NotNull";
    private static final String TARGET_SIZE = "jakarta.validation.constraints.Size";
    private static final Set<String> SIZE_ANNOTATIONS = Set.of("Length", "MaxLength", "MinLength");

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
        return MigrationItemType.VALIDATION;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_VALIDATION;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.VALIDATION
                && item.getTargetKey() == MigrationTarget.SPRING_VALIDATION
                && file.getFileType() == FileType.JAVA;
    }

    @Override
    public String propose(String source, String relativePath) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        List<AnnotationExpr> required = new ArrayList<>();
        List<AnnotationExpr> sizes = new ArrayList<>();
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            String name = simpleName(annotation.getNameAsString());
            if (name.equals("Required")) {
                required.add(annotation);
            } else if (SIZE_ANNOTATIONS.contains(name)) {
                sizes.add(annotation);
            }
        }
        if (required.isEmpty() && sizes.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "The source has no supported SAStruts validation annotation.");
        }
        List<Edit> edits = new ArrayList<>();
        for (AnnotationExpr annotation : required) {
            if (!(annotation instanceof MarkerAnnotationExpr)) {
                throw new TransformationUnsupportedException(
                        "@Required attributes require manual review.");
            }
            if (hasAnnotation(annotation, "NotNull")) {
                throw new TransformationUnsupportedException(
                        "@Required next to an existing @NotNull requires manual review.");
            }
            edits.add(new Edit(offsets(source, annotation), "@NotNull"));
        }
        Map<Node, List<AnnotationExpr>> sizeByParent = new LinkedHashMap<>();
        for (AnnotationExpr annotation : sizes) {
            annotation.getParentNode().ifPresent(parent ->
                    sizeByParent.computeIfAbsent(parent, ignored -> new ArrayList<>())
                            .add(annotation));
        }
        for (Map.Entry<Node, List<AnnotationExpr>> entry : sizeByParent.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new TransformationUnsupportedException(
                        "Multiple length annotations on one declaration require manual review.");
            }
            AnnotationExpr annotation = entry.getValue().get(0);
            if (hasAnnotation(annotation, "Size")) {
                throw new TransformationUnsupportedException(
                        "A length annotation next to an existing @Size requires manual review.");
            }
            edits.add(new Edit(offsets(source, annotation), sizeAnnotation(annotation)));
        }
        Set<String> neededTargets = new LinkedHashSet<>();
        if (!required.isEmpty()) {
            neededTargets.add(TARGET_NOT_NULL);
        }
        if (!sizes.isEmpty()) {
            neededTargets.add(TARGET_SIZE);
        }
        Set<String> imported = new LinkedHashSet<>();
        for (ImportDeclaration declaration : unit.getImports()) {
            imported.add(declaration.getNameAsString());
        }
        for (String target : neededTargets) {
            for (ImportDeclaration declaration : unit.getImports()) {
                if (!declaration.getNameAsString().equals(target)
                        && simpleName(declaration.getNameAsString())
                        .equals(simpleName(target))) {
                    throw new TransformationUnsupportedException(
                            "A conflicting " + simpleName(target)
                                    + " import requires manual review.");
                }
            }
        }
        List<String> missingTargets = neededTargets.stream()
                .filter(target -> !imported.contains(target))
                .toList();
        Set<String> usedSourceImports = new LinkedHashSet<>();
        if (!required.isEmpty()) {
            usedSourceImports.add(SOURCE_REQUIRED);
        }
        for (AnnotationExpr annotation : sizes) {
            usedSourceImports.add(sourceImport(simpleName(annotation.getNameAsString())));
        }
        List<ImportDeclaration> removable = unit.getImports().stream()
                .filter(declaration -> usedSourceImports.contains(declaration.getNameAsString()))
                .toList();
        edits.addAll(importEdits(source, unit, missingTargets, removable));
        return apply(source, edits);
    }

    private List<Edit> importEdits(String source, CompilationUnit unit,
                                   List<String> missingTargets,
                                   List<ImportDeclaration> removable) {
        List<Edit> edits = new ArrayList<>();
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        if (missingTargets.isEmpty()) {
            for (ImportDeclaration declaration : removable) {
                edits.add(new Edit(importRemovalOffsets(source, declaration), ""));
            }
            return edits;
        }
        String imports = String.join(newline, missingTargets.stream()
                .map(target -> "import " + target + ";")
                .toList());
        if (!removable.isEmpty()) {
            edits.add(new Edit(offsets(source, removable.get(0)), imports));
            for (int index = 1; index < removable.size(); index++) {
                edits.add(new Edit(importRemovalOffsets(source, removable.get(index)), ""));
            }
            return edits;
        }
        if (!unit.getImports().isEmpty()) {
            ImportDeclaration last = unit.getImports().get(unit.getImports().size() - 1);
            int end = offsets(source, last)[1];
            edits.add(new Edit(end, end, newline + imports));
            return edits;
        }
        if (unit.getPackageDeclaration().isPresent()) {
            int end = offsets(source, unit.getPackageDeclaration().get())[1];
            edits.add(new Edit(end, end, newline + imports));
            return edits;
        }
        edits.add(new Edit(0, 0, imports + newline));
        return edits;
    }

    private String sizeAnnotation(AnnotationExpr annotation) {
        String name = simpleName(annotation.getNameAsString());
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            if (!name.equals("MaxLength") && !name.equals("MinLength")) {
                throw new TransformationUnsupportedException(
                        "Only @MaxLength(n) and @MinLength(n) single-value annotations are supported.");
            }
            int value = integerValue(single.getMemberValue());
            String attribute = name.equals("MaxLength") ? "max" : "min";
            return "@Size(" + attribute + " = " + value + ")";
        }
        if (annotation instanceof NormalAnnotationExpr normal && name.equals("Length")) {
            Integer min = null;
            Integer max = null;
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("min")) {
                    min = integerValue(pair.getValue());
                } else if (pair.getNameAsString().equals("max")) {
                    max = integerValue(pair.getValue());
                } else {
                    throw new TransformationUnsupportedException(
                            "@Length supports only min and max attributes.");
                }
            }
            if (min == null && max == null) {
                throw new TransformationUnsupportedException(
                        "@Length requires min or max.");
            }
            if (min != null && max != null && min > max) {
                throw new TransformationUnsupportedException(
                        "@Length min must not exceed max.");
            }
            List<String> parts = new ArrayList<>();
            if (min != null) {
                parts.add("min = " + min);
            }
            if (max != null) {
                parts.add("max = " + max);
            }
            return "@Size(" + String.join(", ", parts) + ")";
        }
        throw new TransformationUnsupportedException(
                "Only @Length(min, max), @MaxLength(n), and @MinLength(n) are supported.");
    }

    private int integerValue(Expression expression) {
        if (!(expression instanceof IntegerLiteralExpr literal)) {
            throw new TransformationUnsupportedException(
                    "Length values must be integer literals.");
        }
        String text = literal.getValue();
        if (!text.matches("[0-9]+")) {
            throw new TransformationUnsupportedException(
                    "Length values must be non-negative decimal integer literals.");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            throw new TransformationUnsupportedException(
                    "Length value is outside the supported integer range.");
        }
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

    private String sourceImport(String simpleName) {
        return switch (simpleName) {
            case "Required" -> SOURCE_REQUIRED;
            case "Length" -> SOURCE_LENGTH;
            case "MaxLength" -> SOURCE_MAX_LENGTH;
            case "MinLength" -> SOURCE_MIN_LENGTH;
            default -> throw new TransformationUnsupportedException(
                    "Unsupported SAStruts validation annotation.");
        };
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
            result = result.substring(0, edit.start()) + edit.replacement()
                    + result.substring(edit.end());
        }
        return result;
    }

    private int[] importRemovalOffsets(String source, ImportDeclaration declaration) {
        int[] range = offsets(source, declaration);
        int index = range[1];
        while (index < source.length()
                && (source.charAt(index) == ' ' || source.charAt(index) == '\t'
                || source.charAt(index) == '\r')) {
            index++;
        }
        if (index < source.length() && source.charAt(index) == '\n') {
            return new int[]{range[0], index + 1};
        }
        return range;
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
