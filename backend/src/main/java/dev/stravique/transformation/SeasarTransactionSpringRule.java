package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class SeasarTransactionSpringRule implements TransformationRule {

    private static final String RULE_ID = "S2TX-SPRING-TRANSACTIONAL";
    private static final String RULE_VERSION = "1.1.0";
    private static final String SEASAR_ANNOTATION =
            "org.seasar.extension.tx.annotation.TransactionAttribute";
    private static final String SEASAR_TYPE =
            "org.seasar.extension.tx.TransactionAttributeType";
    private static final String SPRING_ANNOTATION =
            "org.springframework.transaction.annotation.Transactional";
    private static final String SPRING_PROPAGATION =
            "org.springframework.transaction.annotation.Propagation";

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
        return MigrationItemType.TRANSACTION;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_JDBC;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.TRANSACTION
                && item.getTargetKey() == MigrationTarget.SPRING_JDBC
                && file.getFileType() == FileType.JAVA;
    }

    @Override
    public String propose(String source, String relativePath) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        ImportDeclaration seasarImport = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(SEASAR_ANNOTATION))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The source does not import the Seasar2 transaction annotation."));
        List<Edit> edits = new ArrayList<>();
        boolean found = false;
        boolean explicitPresent = false;
        boolean propagationNeeded = false;
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            if (!simpleName(annotation.getNameAsString()).equals("TransactionAttribute")) {
                continue;
            }
            found = true;
            if (!(annotation instanceof MarkerAnnotationExpr)) {
                explicitPresent = true;
            }
            if (hasAnnotation(annotation, "Transactional")) {
                throw new TransformationUnsupportedException(
                        "@TransactionAttribute next to an existing @Transactional requires manual review.");
            }
            String propagation = propagation(annotation);
            if (propagation != null) {
                propagationNeeded = true;
                edits.add(new Edit(offsets(source, annotation),
                        "@Transactional(propagation = Propagation." + propagation + ")"));
            } else {
                edits.add(new Edit(offsets(source, annotation), "@Transactional"));
            }
        }
        if (!found) {
            throw new TransformationUnsupportedException(
                    "The source imports @TransactionAttribute but does not annotate a type or method.");
        }
        Set<String> neededTargets = new LinkedHashSet<>();
        neededTargets.add(SPRING_ANNOTATION);
        if (propagationNeeded) {
            neededTargets.add(SPRING_PROPAGATION);
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
        Set<String> removableNames = new LinkedHashSet<>();
        removableNames.add(SEASAR_ANNOTATION);
        if (explicitPresent && imported.contains(SEASAR_TYPE)) {
            removableNames.add(SEASAR_TYPE);
        }
        List<ImportDeclaration> removable = unit.getImports().stream()
                .filter(declaration -> removableNames.contains(declaration.getNameAsString()))
                .toList();
        edits.addAll(importEdits(source, missingTargets, removable));
        return apply(source, edits);
    }

    private String propagation(AnnotationExpr annotation) {
        if (annotation instanceof MarkerAnnotationExpr) {
            return null;
        }
        Expression value;
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            value = single.getMemberValue();
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            if (normal.getPairs().isEmpty()) {
                return null;
            }
            if (normal.getPairs().size() != 1
                    || !normal.getPairs().get(0).getNameAsString().equals("value")) {
                throw new TransformationUnsupportedException(
                        "@TransactionAttribute attributes require manual review.");
            }
            value = normal.getPairs().get(0).getValue();
        } else {
            throw new TransformationUnsupportedException(
                    "This @TransactionAttribute form requires manual review.");
        }
        if (!(value instanceof FieldAccessExpr access)
                || !simpleName(access.getScope().toString()).equals("TransactionAttributeType")) {
            throw new TransformationUnsupportedException(
                    "Only TransactionAttributeType constants are supported.");
        }
        String propagation = access.getNameAsString();
        if (propagation.equals("REQUIRED")) {
            return null;
        }
        if (propagation.equals("REQUIRES_NEW")) {
            return propagation;
        }
        throw new TransformationUnsupportedException(
                "Transaction propagation " + propagation + " requires manual review.");
    }

    private List<Edit> importEdits(String source, List<String> missingTargets,
                                   List<ImportDeclaration> removable) {
        List<Edit> edits = new ArrayList<>();
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        if (missingTargets.isEmpty()) {
            for (ImportDeclaration declaration : removable) {
                edits.add(new Edit(removalRange(source, declaration), ""));
            }
            return edits;
        }
        String imports = String.join(newline, missingTargets.stream()
                .map(target -> "import " + target + ";")
                .toList());
        if (!removable.isEmpty()) {
            edits.add(new Edit(offsets(source, removable.get(0)), imports));
            for (int index = 1; index < removable.size(); index++) {
                edits.add(new Edit(removalRange(source, removable.get(index)), ""));
            }
            return edits;
        }
        throw new TransformationUnsupportedException(
                "No source import is available to anchor the transaction imports.");
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
            result = result.substring(0, edit.start()) + edit.replacement()
                    + result.substring(edit.end());
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

    private int[] removalRange(String source, ImportDeclaration importDeclaration) {
        int[] offsets = offsets(source, importDeclaration);
        int lineStart = source.lastIndexOf('\n', offsets[0] - 1) + 1;
        int lineEnd = source.indexOf('\n', offsets[1]);
        int boundedEnd = lineEnd < 0 ? source.length() : lineEnd;
        boolean aloneOnItsLine = source.substring(lineStart, offsets[0]).isBlank()
                && source.substring(offsets[1], boundedEnd).isBlank();
        if (!aloneOnItsLine) {
            return offsets;
        }
        return new int[]{lineStart, lineEnd < 0 ? boundedEnd : lineEnd + 1};
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
