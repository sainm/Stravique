package dev.stravique.transformation;

import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class S2jdbcQuerySpringJdbcRule implements TransformationRule {

    private static final String RULE_ID = "S2JDBC-QUERY-SPRING-JDBC";
    private static final String RULE_VERSION = "1.1.0";
    private static final String S2_JDBC_MANAGER = "org.seasar.extension.jdbc.JdbcManager";
    private static final String SPRING_JDBC_TEMPLATE = "org.springframework.jdbc.core.JdbcTemplate";
    private static final String SPRING_ROW_MAPPER =
            "org.springframework.jdbc.core.BeanPropertyRowMapper";
    private static final Set<String> WRITE_CALLS =
            Set.of("insertBySql", "updateBySql", "deleteBySql");
    private static final Map<String, String> SQL_TYPES = Map.ofEntries(
            Map.entry("String", "VARCHAR"),
            Map.entry("Integer", "INTEGER"),
            Map.entry("int", "INTEGER"),
            Map.entry("Long", "BIGINT"),
            Map.entry("long", "BIGINT"),
            Map.entry("Short", "SMALLINT"),
            Map.entry("short", "SMALLINT"),
            Map.entry("Boolean", "BOOLEAN"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("Double", "DOUBLE"),
            Map.entry("double", "DOUBLE"),
            Map.entry("Float", "REAL"),
            Map.entry("float", "REAL"),
            Map.entry("BigDecimal", "DECIMAL"),
            Map.entry("Timestamp", "TIMESTAMP"),
            Map.entry("byte[]", "VARBINARY")
    );

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
        return MigrationItemType.DATA_ACCESS;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_JDBC;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.DATA_ACCESS
                && item.getTargetKey() == MigrationTarget.SPRING_JDBC
                && file.getFileType() == FileType.JAVA;
    }

    @Override
    public String propose(String source, String relativePath) {
        CompilationUnit unit = StaticJavaParser.parse(source);
        ImportDeclaration managerImport = unit.getImports().stream()
                .filter(candidate -> candidate.getNameAsString().equals(S2_JDBC_MANAGER))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The source does not import JdbcManager."));
        List<FieldDeclaration> fields = unit.findAll(FieldDeclaration.class).stream()
                .filter(field -> field.getVariables().stream()
                        .anyMatch(variable -> simpleName(variable.getType().asString())
                                .equals("JdbcManager")))
                .toList();
        if (fields.size() != 1 || fields.getFirst().getVariables().size() != 1) {
            throw new TransformationUnsupportedException(
                    "The source must declare exactly one JdbcManager field.");
        }
        VariableDeclarator variable = fields.getFirst().getVariable(0);
        if (variable.getInitializer().isPresent()) {
            throw new TransformationUnsupportedException(
                    "An initialized JdbcManager field requires manual review.");
        }
        String fieldName = variable.getNameAsString();
        List<MethodCallExpr> calls = new ArrayList<>();
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (!scopeUsesField(call, fieldName)) {
                continue;
            }
            calls.add(call);
        }
        if (calls.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "The source does not use the JdbcManager field.");
        }
        List<Edit> edits = new ArrayList<>();
        boolean writes = false;
        for (MethodCallExpr call : calls) {
            if (WRITE_CALLS.contains(call.getNameAsString())) {
                writes = true;
                edits.add(new Edit(offsets(source, writeChain(call)),
                        writeReplacement(fieldName, call)));
                continue;
            }
            if (!call.getNameAsString().equals("selectBySql")) {
                throw new TransformationUnsupportedException(
                        "Only selectBySql, insertBySql, updateBySql, and deleteBySql calls are"
                                + " supported; " + call.getNameAsString() + " requires manual review.");
            }
            MethodCallExpr resultCall = call.getParentNode()
                    .filter(MethodCallExpr.class::isInstance)
                    .map(MethodCallExpr.class::cast)
                    .filter(parent -> parent.getScope().orElse(null) == call)
                    .filter(parent -> parent.getNameAsString().equals("getResultList"))
                    .orElseThrow(() -> new TransformationUnsupportedException(
                            "selectBySql must end with getResultList()."));
            List<Expression> arguments = call.getArguments();
            if (arguments.size() < 2
                    || !(arguments.get(0) instanceof ClassExpr entity)
                    || !(arguments.get(1) instanceof StringLiteralExpr sql)) {
                throw new TransformationUnsupportedException(
                        "The selectBySql call shape requires a class literal and SQL string.");
            }
            StringBuilder replacement = new StringBuilder(fieldName).append(".query(")
                    .append(sql)
                    .append(", new BeanPropertyRowMapper<>(")
                    .append(entity)
                    .append(")");
            for (int index = 2; index < arguments.size(); index++) {
                replacement.append(", ").append(arguments.get(index));
            }
            replacement.append(")");
            edits.add(new Edit(offsets(source, resultCall), replacement.toString()));
        }
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            if (!simpleName(type.asString()).equals("JdbcManager")) {
                continue;
            }
            if (type == variable.getType()
                    || type.getRange().equals(variable.getType().getRange())) {
                continue;
            }
            throw new TransformationUnsupportedException(
                    "Additional JdbcManager type usage requires manual review.");
        }
        edits.add(new Edit(offsets(source, variable.getType()), "JdbcTemplate"));
        String importBlock = importBlock(unit, writes);
        edits.add(importBlock.isEmpty()
                ? new Edit(removalRange(source, managerImport), "")
                : new Edit(offsets(source, managerImport), importBlock));
        return apply(source, edits);
    }

    private MethodCallExpr writeChain(MethodCallExpr call) {
        MethodCallExpr execute = call.getParentNode()
                .filter(MethodCallExpr.class::isInstance)
                .map(MethodCallExpr.class::cast)
                .filter(parent -> parent.getScope().orElse(null) == call)
                .filter(parent -> parent.getNameAsString().equals("execute"))
                .orElseThrow(() -> new TransformationUnsupportedException(
                        call.getNameAsString() + " must end with execute()."));
        if (execute.getParentNode().filter(MethodCallExpr.class::isInstance).isPresent()) {
            throw new TransformationUnsupportedException(
                    "A chained call after execute() requires manual review.");
        }
        return execute;
    }

    private String writeReplacement(String fieldName, MethodCallExpr call) {
        List<Expression> arguments = call.getArguments();
        if (arguments.isEmpty() || !(arguments.getFirst() instanceof StringLiteralExpr sql)) {
            throw new TransformationUnsupportedException(
                    "The " + call.getNameAsString() + " call shape requires a SQL string literal.");
        }
        List<String> sqlTypes = new ArrayList<>();
        for (int index = 1; index < arguments.size(); index++) {
            if (!(arguments.get(index) instanceof ClassExpr classExpr)) {
                throw new TransformationUnsupportedException(
                        "The declared parameter types must be class literals.");
            }
            String simple = simpleName(classExpr.getType().asString());
            String sqlType = SQL_TYPES.get(simple);
            if (sqlType == null) {
                throw new TransformationUnsupportedException(
                        "The parameter type " + simple + " has no vetted SQL type mapping.");
            }
            sqlTypes.add(sqlType);
        }
        List<Expression> parameters = writeChain(call).getArguments();
        if (parameters.size() != sqlTypes.size()) {
            throw new TransformationUnsupportedException(
                    "The number of bound parameters does not match the declared parameter types.");
        }
        StringBuilder replacement = new StringBuilder(fieldName).append(".update(").append(sql);
        if (!parameters.isEmpty()) {
            replacement.append(", new Object[]{");
            for (int index = 0; index < parameters.size(); index++) {
                if (index > 0) {
                    replacement.append(", ");
                }
                replacement.append(parameters.get(index));
            }
            replacement.append("}, new int[]{");
            for (int index = 0; index < sqlTypes.size(); index++) {
                if (index > 0) {
                    replacement.append(", ");
                }
                replacement.append("Types.").append(sqlTypes.get(index));
            }
            replacement.append("}");
        }
        return replacement.append(")").toString();
    }

    private boolean scopeUsesField(MethodCallExpr call, String fieldName) {
        String scope = call.getScope().map(Object::toString).orElse("");
        return scope.equals(fieldName) || scope.equals("this." + fieldName);
    }

    private String importBlock(CompilationUnit unit, boolean needsSqlTypes) {
        List<String> imports = new ArrayList<>();
        if (!hasImport(unit, SPRING_JDBC_TEMPLATE)) {
            imports.add(SPRING_JDBC_TEMPLATE);
        }
        if (!hasImport(unit, SPRING_ROW_MAPPER)) {
            imports.add(SPRING_ROW_MAPPER);
        }
        if (needsSqlTypes && !hasImport(unit, "java.sql.Types")) {
            imports.add("java.sql.Types");
        }
        StringBuilder block = new StringBuilder();
        for (String imported : imports) {
            if (block.length() > 0) {
                block.append("\n");
            }
            block.append("import ").append(imported).append(";");
        }
        return block.toString();
    }

    private boolean hasImport(CompilationUnit unit, String name) {
        return unit.getImports().stream()
                .anyMatch(candidate -> candidate.getNameAsString().equals(name));
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