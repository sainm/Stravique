package dev.stravique.inventory;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class JavaInventoryAnalyzer {

    private static final Set<String> SEASAR_BINDINGS = Set.of(
            "Binding", "AutoBinding", "Component", "Aspect", "Interceptor", "Resource"
    );
    private static final Set<String> SASTRUTS_VALIDATION_ANNOTATIONS = Set.of(
            "Required", "Validators", "Validator", "Min", "Max", "Size", "Pattern", "Email",
            "Number", "Date", "IntegerRange", "LongRange", "DoubleRange", "FloatRange"
    );

    private final FindingFactory findings;

    public JavaInventoryAnalyzer(FindingFactory findings) {
        this.findings = findings;
    }

    public AnalysisResult analyze(AnalyzerContext context) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(context.text());
            List<AnalysisFinding> result = new ArrayList<>();
            addLanguageEvidence(context, result);
            analyzeImports(context, unit, result);
            analyzeTypes(context, unit, result);
            analyzeCalls(context, unit, result);
            return new AnalysisResult(List.copyOf(result), ParseStatus.PARSED, null);
        } catch (Exception exception) {
            String message = exception.getMessage();
            return new AnalysisResult(
                    List.of(findings.parseFailure(context, "JAVA-PARSE-001",
                            exception.getClass().getSimpleName(), message)),
                    ParseStatus.FAILED,
                    message
            );
        }
    }

    private void addLanguageEvidence(AnalyzerContext context, List<AnalysisFinding> result) {
        context.ledger().recordApi("JAVA", "Java", context.relativePath(), 1, "JAVA-LANG-001");
        result.add(findings.create(context, AnalysisKind.TECHNOLOGY, "JAVA",
                "Java source file identified.", "JAVA-LANG-001", 1, Certainty.ANALYZED,
                ParseStatus.PARSED, null));
    }

    private void analyzeImports(AnalyzerContext context, CompilationUnit unit,
                                List<AnalysisFinding> result) {
        for (ImportDeclaration declaration : unit.getImports()) {
            int line = line(declaration.getBegin());
            String imported = declaration.getNameAsString();
            if (imported.startsWith("org.seasar.struts")) {
                api(context, result, "SASTRUTS", "SAStruts", line,
                        "SAStruts API import detected.", "JAVA-IMPORT-SAST-001");
            }
            if (imported.startsWith("org.apache.struts")) {
                api(context, result, "STRUTS1", "Struts 1", line,
                        "Struts 1 API import detected.", "JAVA-IMPORT-STR1-001");
            }
            if (imported.startsWith("org.seasar.framework")) {
                api(context, result, "SEASAR2", "Seasar2", line,
                        "Seasar2 container API import detected.", "JAVA-IMPORT-SEASAR-001");
            }
            if (imported.startsWith("org.seasar.extension.jdbc")) {
                api(context, result, "S2JDBC", "S2JDBC", line,
                        "S2JDBC API import detected.", "JAVA-IMPORT-S2JDBC-001");
            }
            if (imported.startsWith("org.seasar.extension.tx")
                    || imported.startsWith("org.seasar.extension.jta")) {
                api(context, result, "S2JDBC", "S2JDBC", line,
                        "S2 transaction API import detected.", "JAVA-S2JDBC-TX-001");
            }
            if (imported.startsWith("javax.servlet") || imported.startsWith("jakarta.servlet")) {
                api(context, result, "SERVLET", "Servlet API", line,
                        "Servlet API import detected.", "JAVA-IMPORT-SERVLET-001");
            }
            if (imported.startsWith("javax.servlet.jsp.jstl") || imported.startsWith("jakarta.servlet.jsp.jstl")) {
                api(context, result, "JSTL", "JSTL", line,
                        "JSTL API import detected.", "JAVA-IMPORT-JSTL-001");
            }
            if (imported.startsWith("org.junit") || imported.startsWith("junit.framework")) {
                api(context, result, "JUNIT", "JUnit", line,
                        "JUnit API import detected.", "JAVA-IMPORT-JUNIT-001");
            }
            if (imported.startsWith("java.sql") || imported.startsWith("javax.sql")) {
                api(context, result, "DB_ACCESS", "JDBC / database access", line,
                        "JDBC API import detected.", "JAVA-IMPORT-JDBC-001");
            }
            if (isJakartaCandidate(imported)) {
                result.add(findings.create(context, AnalysisKind.FRAMEWORK_API, "JAKARTA",
                        "Legacy javax namespace import " + imported
                                + " requires Jakarta migration for Spring Boot 3.",
                        "JAVA-JAKARTA-NAMESPACE-001", line, Certainty.ANALYZED,
                        ParseStatus.PARSED, "{\"import\":\"" + imported + "\"}"));
            }
            if (isCustomFrameworkImport(imported)) {
                inferredApi(context, result, "CUSTOM_FRAMEWORK", "Custom business framework", line,
                        "Project-owned framework package import requires review.",
                        "JAVA-CUSTOM-FRAMEWORK-001");
            }
        }
    }

    private void analyzeTypes(AnalyzerContext context, CompilationUnit unit,
                              List<AnalysisFinding> result) {
        for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = type.getNameAsString();
            String imports = importsAsText(unit);
            boolean saStrutsContext = imports.contains("org.seasar.struts");
            boolean strutsContext = imports.contains("org.apache.struts");
            boolean seasarContext = imports.contains("org.seasar.framework")
                    || imports.contains("org.seasar.struts");
            boolean s2jdbcContext = imports.contains("org.seasar.extension.jdbc");

            for (ClassOrInterfaceType extended : type.getExtendedTypes()) {
                String value = extended.asString();
                int line = line(extended.getBegin());
                if (value.endsWith("Action") && saStrutsContext) {
                    api(context, result, "SASTRUTS", "SAStruts", line,
                            "Class inherits a SAStruts action base type.", "JAVA-SAST-ACTION-001");
                } else if (value.endsWith("Action") && strutsContext) {
                    api(context, result, "STRUTS1", "Struts 1", line,
                            "Class inherits the Struts 1 Action base type.", "JAVA-STR1-ACTION-001");
                }
                if (value.endsWith("ActionForm") && strutsContext) {
                    api(context, result, "STRUTS1", "Struts 1", line,
                            "Struts 1 ActionForm inheritance detected.", "JAVA-STR1-FORM-001");
                }
            }

            if (seasarContext && className.endsWith("Action")) {
                api(context, result, "SASTRUTS", "SAStruts", line(type.getBegin()),
                        "Action class identified in a Seasar/SAStruts source context.",
                        "JAVA-SAST-ACTION-CLASS-001");
            }
            if (s2jdbcContext && className.endsWith("Dao")) {
                api(context, result, "S2JDBC", "S2JDBC", line(type.getBegin()),
                        "DAO class identified in an S2JDBC source context.", "JAVA-S2JDBC-DAO-001");
            }

            for (AnnotationExpr annotation : type.getAnnotations()) {
                String annotationName = simpleName(annotation.getNameAsString());
                int annotationLine = line(annotation.getBegin());
                if (annotationName.equals("Execute") && saStrutsContext) {
                    api(context, result, "SASTRUTS", "SAStruts", annotationLine,
                            "SAStruts action method annotation detected.", "JAVA-SAST-EXECUTE-001");
                }
                if (annotationName.equals("Entity") && s2jdbcContext) {
                    api(context, result, "S2JDBC", "S2JDBC", annotationLine,
                            "S2JDBC entity annotation detected.", "JAVA-S2JDBC-ENTITY-001");
                }
                if (SEASAR_BINDINGS.contains(annotationName) && seasarContext) {
                    api(context, result, "SEASAR2", "Seasar2", annotationLine,
                            "Seasar2 component or binding annotation detected.", "JAVA-SEASAR-ANN-001");
                }
                if (annotationName.equals("Table") && s2jdbcContext) {
                    api(context, result, "S2JDBC", "S2JDBC", annotationLine,
                            "S2JDBC table mapping annotation detected.", "JAVA-S2JDBC-TABLE-001");
                }
            }

            for (var field : type.getFields()) {
                for (AnnotationExpr annotation : field.getAnnotations()) {
                    String annotationName = simpleName(annotation.getNameAsString());
                    if (SEASAR_BINDINGS.contains(annotationName) && seasarContext) {
                        api(context, result, "SEASAR2", "Seasar2", line(annotation.getBegin()),
                                "Seasar2 field injection or binding annotation detected.",
                                "JAVA-SEASAR-FIELD-001");
                    }
                    if (annotationName.equals("ActionForm") && saStrutsContext) {
                        api(context, result, "SASTRUTS", "SAStruts", line(annotation.getBegin()),
                                "SAStruts action form binding annotation detected.",
                                "JAVA-SAST-FORM-001");
                    }
                    if (SASTRUTS_VALIDATION_ANNOTATIONS.contains(annotationName) && saStrutsContext) {
                        api(context, result, "SASTRUTS", "SAStruts", line(annotation.getBegin()),
                                "SAStruts validation annotation detected.",
                                "JAVA-SAST-VALIDATION-001");
                    }
                }
            }

            for (MethodDeclaration method : type.getMethods()) {
                for (AnnotationExpr annotation : method.getAnnotations()) {
                    String annotationName = simpleName(annotation.getNameAsString());
                    if (annotationName.equals("Execute") && saStrutsContext) {
                        api(context, result, "SASTRUTS", "SAStruts", line(annotation.getBegin()),
                                "SAStruts execute method detected.", "JAVA-SAST-EXECUTE-002");
                    }
                }
                if (method.getNameAsString().equals("execute") && strutsContext
                        && method.getParameters().stream()
                        .anyMatch(parameter -> parameter.getType().asString().endsWith("ActionMapping"))) {
                    api(context, result, "STRUTS1", "Struts 1", line(method.getBegin()),
                            "Struts 1 execute method signature detected.", "JAVA-STR1-EXECUTE-001");
                }
                if (method.getNameAsString().equals("validate") && strutsContext
                        && method.getParameters().stream()
                        .anyMatch(parameter -> parameter.getType().asString().endsWith("ActionMapping"))) {
                    api(context, result, "STRUTS1", "Struts 1", line(method.getBegin()),
                            "Struts 1 validation method signature detected.",
                            "JAVA-STR1-VALIDATION-001");
                }
            }
        }
    }

    private void analyzeCalls(AnalyzerContext context, CompilationUnit unit,
                              List<AnalysisFinding> result) {
        boolean s2jdbcContext = importsAsText(unit).contains("org.seasar.extension.jdbc");
        boolean strutsContext = importsAsText(unit).contains("org.apache.struts")
                || importsAsText(unit).contains("org.seasar.struts");
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            int line = line(call.getBegin());
            String scope = call.getScope().map(Object::toString).orElse("");
            if (s2jdbcContext && (name.startsWith("select") || name.startsWith("insert")
                    || name.startsWith("update") || name.startsWith("delete")
                    || name.endsWith("BySql") || name.equals("execute"))) {
                api(context, result, "S2JDBC", "S2JDBC", line,
                        "S2JDBC database operation call detected.", "JAVA-S2JDBC-QUERY-001");
            }
            if (strutsContext && (name.startsWith("save") || name.startsWith("add"))
                    && (name.contains("Message") || name.contains("Error"))) {
                api(context, result, "STRUTS1", "Struts 1", line,
                        "Struts message or error lifecycle API detected.", "JAVA-STR1-MESSAGE-001");
            }
            if (strutsContext && name.equals("findForward")) {
                String target = call.getArgument(0) instanceof StringLiteralExpr literal
                        ? literal.getValue() : "";
                context.ledger().recordApi("STRUTS1", "Struts 1", context.relativePath(),
                        line, "JAVA-STR1-FORWARD-001");
                result.add(findings.create(context, AnalysisKind.FRAMEWORK_API, "STRUTS1",
                        "Struts forward candidate" + (target.isBlank() ? "" : " " + target) + ".",
                        "JAVA-STR1-FORWARD-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"target\":\"" + target.replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\"}"));
            }
            if ((scope.endsWith("SingletonS2ContainerFactory") && name.equals("init"))
                    || (scope.endsWith("S2ContainerFactory") && name.equals("create"))) {
                api(context, result, "SEASAR2", "Seasar2", line,
                        "Seasar2 container initialization call detected.",
                        "JAVA-SEASAR-CONTAINER-001");
            }
            if (name.startsWith("forward") || name.startsWith("redirect")) {
                result.add(findings.create(context, AnalysisKind.FRAMEWORK_API,
                        "REQUEST_NAVIGATION", "Request forwarding or redirect call observed.",
                        "JAVA-NAV-001", line, Certainty.ANALYZED, ParseStatus.PARSED, null));
            }
            boolean dynamicLoad = (scope.endsWith("Class") && name.equals("forName"))
                    || (scope.toLowerCase(Locale.ROOT).contains("classloader") && name.equals("loadClass"))
                    || (scope.equals("ServiceLoader") && name.equals("load"));
            if (dynamicLoad) {
                result.add(findings.create(context, AnalysisKind.REVIEW_ITEM,
                        "DYNAMIC_CLASS_LOADING",
                        "Dynamic class loading requires runtime or configuration evidence.",
                        "JAVA-DYNAMIC-001", line, Certainty.REVIEW_REQUIRED, ParseStatus.PARSED,
                        "{\"reason\":\"dynamic-loading\"}"));
            }
            if (Set.of("getDeclaredMethod", "getMethod", "newInstance").contains(name)
                    && !scope.isBlank()) {
                result.add(findings.create(context, AnalysisKind.REVIEW_ITEM,
                        "REFLECTION_CANDIDATE",
                        "Reflective invocation is not treated as a confirmed dependency.",
                        "JAVA-REFLECTION-001", line, Certainty.REVIEW_REQUIRED, ParseStatus.PARSED,
                        "{\"reason\":\"reflection\"}"));
            }
        }
    }

    private void api(AnalyzerContext context, List<AnalysisFinding> result, String key, String name,
                     int line, String summary, String ruleId) {
        context.ledger().recordApi(key, name, context.relativePath(), line, ruleId);
        result.add(findings.create(context, AnalysisKind.FRAMEWORK_API, key, summary, ruleId, line,
                Certainty.ANALYZED, ParseStatus.PARSED, null));
    }

    private void inferredApi(AnalyzerContext context, List<AnalysisFinding> result, String key,
                             String name, int line, String summary, String ruleId) {
        context.ledger().recordInferredApi(key, name, context.relativePath(), line, ruleId);
        result.add(findings.create(context, AnalysisKind.FRAMEWORK_API, key, summary, ruleId, line,
                Certainty.REVIEW_REQUIRED, ParseStatus.PARSED,
                "{\"reason\":\"project-owned-framework-package\"}"));
    }

    private boolean isJakartaCandidate(String imported) {
        return List.of("javax.servlet", "javax.annotation", "javax.persistence",
                        "javax.validation", "javax.inject", "javax.transaction",
                        "javax.xml.bind", "javax.ws.rs", "javax.mail", "javax.activation")
                .stream()
                .anyMatch(javax -> imported.equals(javax) || imported.startsWith(javax + "."));
    }

    private boolean isCustomFrameworkImport(String imported) {
        String lower = imported.toLowerCase(Locale.ROOT);
        if (lower.startsWith("org.spring") || lower.startsWith("org.seasar")
                || lower.startsWith("org.apache") || lower.startsWith("org.hibernate")
                || lower.startsWith("org.mybatis") || lower.startsWith("org.junit")
                || lower.startsWith("java.") || lower.startsWith("javax.")
                || lower.startsWith("jakarta.")) {
            return false;
        }
        return lower.contains(".framework.") || lower.contains(".foundation.")
                || lower.contains(".platform.");
    }

    private int line(Optional<com.github.javaparser.Position> position) {
        return position.map(value -> value.line).orElse(1);
    }

    private String importsAsText(CompilationUnit unit) {
        return unit.getImports().stream().map(ImportDeclaration::getNameAsString)
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private String simpleName(String value) {
        int separator = value.lastIndexOf('.');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }
}
