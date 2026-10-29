package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
public class StrutsActionFormModelRule implements TransformationRule {

    private static final String RULE_ID = "STRUTS-ACTIONFORM-MODEL";
    private static final String RULE_VERSION = "1.0.0";
    private static final String STRUTS_ACTION_FORM = "org.apache.struts.action.ActionForm";

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
        return MigrationItemType.FORM_MODEL;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_MVC;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.FORM_MODEL
                && item.getTargetKey() == MigrationTarget.SPRING_MVC
                && file.getFileType() == FileType.JAVA;
    }

    @Override
    public String propose(String source, String relativePath) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        ImportDeclaration actionFormImport = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(STRUTS_ACTION_FORM))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The source does not import the Struts ActionForm base type."));
        ClassOrInterfaceDeclaration declaration = unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The source does not declare a class."));
        ClassOrInterfaceType actionFormType = declaration.getExtendedTypes().stream()
                .filter(type -> simpleName(type.getNameAsString()).equals("ActionForm"))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The class does not extend ActionForm."));
        if (declaration.getExtendedTypes().size() != 1) {
            throw new TransformationUnsupportedException(
                    "The class has additional superclass structure.");
        }
        if (declaration.getMethodsByName("reset").size() > 0
                || declaration.getMethodsByName("validate").size() > 0) {
            throw new TransformationUnsupportedException(
                    "ActionForm lifecycle methods require manual review.");
        }
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(offsets(source, actionFormImport), ""));
        edits.add(new Edit(extendsOffsets(source, actionFormType), ""));
        String result = apply(source, edits);
        if (result.contains("ActionForm")) {
            throw new TransformationUnsupportedException(
                    "ActionForm references remain outside the base type.");
        }
        return result;
    }

    private int[] extendsOffsets(String source, ClassOrInterfaceType type) {
        int[] typeOffsets = offsets(source, type);
        int start = typeOffsets[0];
        int cursor = start - 1;
        while (cursor >= 0 && Character.isWhitespace(source.charAt(cursor))) {
            cursor--;
        }
        int keywordEnd = cursor + 1;
        int keywordStart = keywordEnd - "extends".length();
        if (keywordStart < 0 || !source.substring(keywordStart, keywordEnd).equals("extends")) {
            throw new TransformationUnsupportedException(
                    "The ActionForm superclass range could not be removed safely.");
        }
        return new int[]{keywordStart, typeOffsets[1]};
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
