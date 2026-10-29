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

@Component
public class SaStrutsValidationSpringRule implements TransformationRule {

    private static final String RULE_ID = "SASTRUTS-VALIDATION-BEAN";
    private static final String RULE_VERSION = "1.0.0";
    private static final String SEASAR_REQUIRED = "org.seasar.struts.annotation.Required";
    private static final String BEAN_NOT_NULL = "jakarta.validation.constraints.NotNull";

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
        ImportDeclaration requiredImport = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(SEASAR_REQUIRED))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The source does not import a supported SAStruts validation annotation."));
        List<Edit> edits = new ArrayList<>();
        boolean targetPresent = unit.getImports().stream()
                .anyMatch(candidate -> candidate.getNameAsString().equals(BEAN_NOT_NULL));
        if (targetPresent) {
            edits.add(new Edit(offsets(source, requiredImport), ""));
        } else {
            edits.add(new Edit(offsets(source, requiredImport.getName()), BEAN_NOT_NULL));
        }
        boolean found = false;
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            if (!simpleName(annotation.getNameAsString()).equals("Required")) {
                continue;
            }
            if (!(annotation instanceof MarkerAnnotationExpr)) {
                throw new TransformationUnsupportedException(
                        "@Required attributes require manual review.");
            }
            found = true;
            edits.add(new Edit(offsets(source, annotation), "@NotNull"));
        }
        if (!found) {
            throw new TransformationUnsupportedException(
                    "The source imports @Required but does not use a supported annotation.");
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
