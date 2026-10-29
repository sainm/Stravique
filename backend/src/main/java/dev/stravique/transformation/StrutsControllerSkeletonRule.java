package dev.stravique.transformation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationArtifactType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class StrutsControllerSkeletonRule implements TransformationRule {

    private static final String RULE_ID = "STRUTS-CONTROLLER-SKELETON";
    private static final String RULE_VERSION = "1.0.0";

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
        return MigrationItemType.ACTION_CONTROLLER;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_MVC;
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.ACTION_CONTROLLER
                && item.getTargetKey() == MigrationTarget.SPRING_MVC
                && file.getFileType() == dev.stravique.domain.FileType.JAVA;
    }

    @Override
    public TransformationDraft propose(TransformationRequest request) {
        CompilationUnit unit = StaticJavaParser.parse(request.source());
        ClassOrInterfaceDeclaration declaration = unit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "The action source does not declare a class."));
        String packageName = unit.getPackageDeclaration()
                .map(packageDeclaration -> packageDeclaration.getNameAsString())
                .orElse("");
        String className = declaration.getNameAsString();
        String fqcn = packageName.isBlank() ? className : packageName + "." + className;
        List<ControllerRoute> routes = request.routes().stream()
                .filter(route -> route.actionType().equals(fqcn)
                        || route.actionType().equals(className))
                .filter(route -> route.actionPath() != null && !route.actionPath().isBlank())
                .filter(route -> route.forwardPath() != null && !route.forwardPath().isBlank())
                .toList();
        if (routes.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "No explicit Struts action mapping with a forward points at this action.");
        }
        Set<String> methodNames = new LinkedHashSet<>();
        declaration.getMethods().stream()
                .filter(method -> method.getAnnotations().stream()
                        .anyMatch(annotation -> simpleName(annotation.getNameAsString())
                                .equals("Execute")))
                .map(MethodDeclaration::getNameAsString)
                .forEach(methodNames::add);
        String controllerName = className + "Controller";
        String newFileName = controllerName + ".java";
        String sourcePath = request.relativePath();
        int separator = sourcePath.lastIndexOf('/');
        String targetPath = (separator < 0 ? "" : sourcePath.substring(0, separator + 1))
                + newFileName;
        StringBuilder source = new StringBuilder();
        if (!packageName.isBlank()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("import org.springframework.stereotype.Controller;\n")
                .append("import org.springframework.web.bind.annotation.RequestMapping;\n\n")
                .append("@Controller\n")
                .append("public class ").append(controllerName).append(" {\n");
        Set<String> usedMethods = new LinkedHashSet<>();
        for (ControllerRoute route : routes) {
            String methodName = methodName(route.actionPath(), methodNames, usedMethods);
            usedMethods.add(methodName);
            source.append("\n    @RequestMapping(\"").append(route.actionPath()).append("\")\n")
                    .append("    public String ").append(methodName).append("() {\n")
                    .append("        return \"").append(viewName(route.forwardPath())).append("\";\n")
                    .append("    }\n");
        }
        source.append("}\n");
        return new TransformationDraft(TransformationArtifactType.CREATE_FILE, targetPath,
                source.toString());
    }

    private String methodName(String actionPath, Set<String> actionMethods, Set<String> used) {
        String stripped = actionPath.replaceAll("^/+", "").replaceAll("/+$", "");
        String last = stripped.isEmpty() ? "root"
                : stripped.substring(stripped.lastIndexOf('/') + 1);
        String candidate = sanitize(last);
        if (!actionMethods.contains(candidate)) {
            candidate = stripped.equals(last) && actionMethods.contains("index")
                    ? "index" : "handle" + capitalize(candidate);
        }
        String result = candidate.isBlank() ? "handleRoot" : candidate;
        int suffix = 2;
        while (used.contains(result)) {
            result = candidate + suffix++;
        }
        return result;
    }

    private String viewName(String forwardPath) {
        if (forwardPath == null || forwardPath.isBlank()) {
            return "";
        }
        String value = forwardPath.replace('\\', '/');
        int webInf = value.indexOf("/WEB-INF/");
        if (webInf >= 0) {
            value = value.substring(webInf + "/WEB-INF/".length());
        }
        if (value.startsWith("jsp/")) {
            value = value.substring(4);
        }
        if (value.endsWith(".jsp")) {
            value = value.substring(0, value.length() - 4);
        }
        return value.replaceAll("^/+", "");
    }

    private String sanitize(String value) {
        StringBuilder result = new StringBuilder();
        for (char character : value.toCharArray()) {
            result.append(Character.isLetterOrDigit(character) ? character : '_');
        }
        return result.toString();
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String simpleName(String value) {
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }
}