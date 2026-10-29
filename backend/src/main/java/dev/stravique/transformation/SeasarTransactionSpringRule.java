package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rewrites a bare Seasar2 {@code @TransactionAttribute} as Spring's {@code @Transactional}.
 *
 * <p>Both annotations default to a required transaction when they carry no attributes, so the
 * replacement keeps the declared behaviour. A parameterized annotation is refused: Seasar's
 * {@code TransactionAttributeType} and Spring's {@code Propagation} do not name their values
 * the same way, and guessing that mapping would change the transaction boundary of a method
 * without any evidence for it. Configuring the transaction manager stays manual, so the
 * planner keeps items of this type in review.
 */
@Component
public class SeasarTransactionSpringRule implements TransformationRule {

    private static final String RULE_ID = "S2TX-SPRING-TRANSACTIONAL";
    private static final String RULE_VERSION = "1.0.0";
    private static final String SEASAR_ANNOTATION =
            "org.seasar.extension.tx.annotation.TransactionAttribute";
    private static final String SPRING_ANNOTATION =
            "org.springframework.transaction.annotation.Transactional";

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
        boolean springImportPresent = unit.getImports().stream()
                .anyMatch(candidate -> candidate.getNameAsString().equals(SPRING_ANNOTATION));
        if (springImportPresent) {
            edits.add(new Edit(removalRange(source, seasarImport), ""));
        } else {
            edits.add(new Edit(offsets(source, seasarImport.getName()), SPRING_ANNOTATION));
        }
        boolean found = false;
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            if (!simpleName(annotation.getNameAsString()).equals("TransactionAttribute")) {
                continue;
            }
            if (!(annotation instanceof MarkerAnnotationExpr)) {
                throw new TransformationUnsupportedException(
                        "@TransactionAttribute attributes require manual review.");
            }
            found = true;
            edits.add(new Edit(offsets(source, annotation), "@Transactional"));
        }
        if (!found) {
            throw new TransformationUnsupportedException(
                    "The source imports @TransactionAttribute but does not annotate a type or method.");
        }
        return apply(source, edits);
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
