package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConfigurationInventoryAnalyzer {

    private static final Pattern PROPERTY_REFERENCE = Pattern.compile(
            "(?i)(?:class|type|dao|entity|action|component|implementation)\\s*[=:]\\s*([\\w.$]+)");
    private static final Pattern PROPERTY_ANY_CLASS = Pattern.compile(
            "^[\\w.-]+\\s*[=:]\\s*((?:[a-z_$][\\w$]*\\.){2,}[A-Z][\\w$]*(?:\\.[A-Z][\\w$]*)?)\\s*$");

    private final FindingFactory findings;

    public ConfigurationInventoryAnalyzer(FindingFactory findings) {
        this.findings = findings;
    }

    public AnalysisResult analyze(AnalyzerContext context) {
        return switch (context.fileType()) {
            case MAVEN_POM -> analyzePom(context);
            case GRADLE -> analyzeGradle(context);
            case DICON -> analyzeDicon(context);
            case TLD -> analyzeTld(context);
            case XML -> analyzeXml(context);
            case PROPERTIES, YAML -> analyzeKeyValue(context);
            default -> AnalysisResult.empty(ParseStatus.NOT_APPLICABLE);
        };
    }

    private AnalysisResult analyzePom(AnalyzerContext context) {
        try {
            Document document = SecureXml.parse(context.text());
            List<AnalysisFinding> output = new ArrayList<>();
            context.ledger().recordDependency("MAVEN", "Maven", context.relativePath(), 1,
                    "POM-BUILD-001");
            output.add(findings.create(context, AnalysisKind.TECHNOLOGY, "MAVEN",
                    "Maven project descriptor identified.", "POM-BUILD-001", 1,
                    Certainty.IDENTIFIED, ParseStatus.PARSED, null));
            NodeList dependencyNodes = document.getElementsByTagNameNS("*", "dependency");
            Set<String> seen = new LinkedHashSet<>();
            for (int index = 0; index < dependencyNodes.getLength(); index++) {
                Element dependency = (Element) dependencyNodes.item(index);
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (artifactId.isBlank()) {
                    continue;
                }
                int artifactLine = findLiteralLine(context.text(), artifactId, 1);
                List<TechSignal> signals = dependencySignals(groupId + ":" + artifactId);
                for (TechSignal signal : signals) {
                    String unique = signal.key() + ":" + artifactLine;
                    if (!seen.add(unique)) {
                        continue;
                    }
                    context.ledger().recordDependency(signal.key(), signal.name(), context.relativePath(),
                            artifactLine, "POM-DEPENDENCY-001");
                    output.add(findings.create(context, AnalysisKind.TECHNOLOGY, signal.key(),
                            "Dependency declaration for " + groupId + ":" + artifactId
                                    + " identified; runtime use is not established.",
                            "POM-DEPENDENCY-001", artifactLine, Certainty.IDENTIFIED,
                            ParseStatus.PARSED,
                            "{\"groupId\":\"" + json(groupId) + "\",\"artifactId\":\""
                                    + json(artifactId) + "\"}"));
                }
            }
            return new AnalysisResult(List.copyOf(output), ParseStatus.PARSED, null);
        } catch (Exception exception) {
            return parseFailure(context, "POM-PARSE-001", exception);
        }
    }

    private AnalysisResult analyzeGradle(AnalyzerContext context) {
        List<AnalysisFinding> output = new ArrayList<>();
        List<String> lines = context.text().lines().toList();
        context.ledger().recordDependency("GRADLE", "Gradle", context.relativePath(), 1,
                "GRADLE-BUILD-001");
        output.add(findings.create(context, AnalysisKind.TECHNOLOGY, "GRADLE",
                "Gradle build file identified.", "GRADLE-BUILD-001", 1,
                Certainty.IDENTIFIED, ParseStatus.PARTIAL, null));
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            int line = index + 1;
            String value = lines.get(index);
            String lower = value.toLowerCase(Locale.ROOT);
            if (!(lower.contains("implementation") || lower.contains("compile")
                    || lower.contains("runtime") || lower.contains("testimplementation")
                    || lower.contains("api "))) {
                continue;
            }
            for (TechSignal signal : dependencySignals(value)) {
                if (seen.add(signal.key() + ":" + line)) {
                    context.ledger().recordDependency(signal.key(), signal.name(), context.relativePath(),
                            line, "GRADLE-DEPENDENCY-001");
                    output.add(findings.create(context, AnalysisKind.TECHNOLOGY, signal.key(),
                            "Gradle dependency declaration identified; runtime use is not established.",
                            "GRADLE-DEPENDENCY-001", line, Certainty.IDENTIFIED, ParseStatus.PARTIAL,
                            null));
                }
            }
        }
        return new AnalysisResult(List.copyOf(output), ParseStatus.PARTIAL, null);
    }

    private AnalysisResult analyzeDicon(AnalyzerContext context) {
        try {
            Document document = SecureXml.parse(context.text());
            List<AnalysisFinding> output = new ArrayList<>();
            NodeList components = document.getElementsByTagName("component");
            for (int index = 0; index < components.getLength(); index++) {
                Element component = (Element) components.item(index);
                String className = firstNonBlank(component.getAttribute("class"),
                        childText(component, "class"));
                String name = firstNonBlank(component.getAttribute("name"), childText(component, "name"));
                String instance = component.getAttribute("instance").trim();
                String autoBinding = component.getAttribute("autoBinding").trim();
                int line = findTagLine(context.text(), "component", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-COMPONENT-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "dicon component definition" + (name.isBlank() ? "" : " for " + name)
                                + scopeSummary(instance, autoBinding) + ".",
                        "DICON-COMPONENT-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"component\":\"" + json(name) + "\",\"class\":\"" + json(className)
                                + "\",\"instance\":\"" + json(instance) + "\",\"autoBinding\":\""
                                + json(autoBinding) + "\"}"));
            }
            NodeList includes = document.getElementsByTagName("include");
            for (int index = 0; index < includes.getLength(); index++) {
                Element include = (Element) includes.item(index);
                String path = include.getAttribute("path").trim();
                int line = findTagLine(context.text(), "include", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-INCLUDE-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "dicon include reference" + (path.isBlank() ? "" : " to " + path) + ".",
                        "DICON-INCLUDE-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"path\":\"" + json(path) + "\"}"));
            }
            NodeList properties = document.getElementsByTagName("property");
            for (int index = 0; index < properties.getLength(); index++) {
                Element property = (Element) properties.item(index);
                String propertyName = property.getAttribute("name");
                String binding = firstNonBlank(property.getAttribute("binding"),
                        childText(property, "binding"));
                int line = findTagLine(context.text(), "property", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-PROPERTY-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "dicon property" + (propertyName.isBlank() ? "" : " " + propertyName)
                                + " configuration detected.",
                        "DICON-PROPERTY-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"property\":\"" + json(propertyName) + "\",\"binding\":\""
                                + json(binding) + "\"}"));
            }
            NodeList arguments = document.getElementsByTagName("arg");
            for (int index = 0; index < arguments.getLength(); index++) {
                Element argument = (Element) arguments.item(index);
                Element nested = directChild(argument, "component");
                String target = nested == null ? argument.getTextContent().trim()
                        : nested.getAttribute("class").trim();
                int line = findTagLine(context.text(), "arg", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-ARG-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "dicon constructor argument" + (target.isBlank() ? "" : " for " + target) + ".",
                        "DICON-ARG-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"target\":\"" + json(target) + "\"}"));
            }
            NodeList aspects = document.getElementsByTagName("aspect");
            for (int index = 0; index < aspects.getLength(); index++) {
                Element aspect = (Element) aspects.item(index);
                String pointcut = aspect.getAttribute("pointcut").trim();
                int line = findTagLine(context.text(), "aspect", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-ASPECT-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "Seasar2 aspect configuration" + (pointcut.isBlank() ? "" : " for " + pointcut) + ".",
                        "DICON-ASPECT-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"pointcut\":\"" + json(pointcut) + "\"}"));
            }
            NodeList interceptors = document.getElementsByTagName("interceptor");
            for (int index = 0; index < interceptors.getLength(); index++) {
                int line = findTagLine(context.text(), "interceptor", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-INTERCEPTOR-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "Seasar2 interceptor configuration detected.", "DICON-INTERCEPTOR-001",
                        line, Certainty.ANALYZED, ParseStatus.PARSED, null));
            }
            NodeList lifecycle = document.getElementsByTagName("initMethod");
            for (int index = 0; index < lifecycle.getLength(); index++) {
                int line = findTagLine(context.text(), "initMethod", index + 1);
                context.ledger().recordConfiguration("SEASAR2", "Seasar2", context.relativePath(),
                        line, "DICON-LIFECYCLE-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SEASAR2",
                        "Seasar2 lifecycle configuration detected.", "DICON-LIFECYCLE-001",
                        line, Certainty.ANALYZED, ParseStatus.PARSED, null));
            }
            return new AnalysisResult(List.copyOf(output), ParseStatus.PARSED, null);
        } catch (Exception exception) {
            return parseFailure(context, "DICON-PARSE-001", exception);
        }
    }

    private String scopeSummary(String instance, String autoBinding) {
        List<String> values = new ArrayList<>();
        if (!instance.isBlank()) {
            values.add("instance " + instance);
        }
        if (!autoBinding.isBlank()) {
            values.add("autoBinding " + autoBinding);
        }
        return values.isEmpty() ? "" : " (" + String.join(", ", values) + ")";
    }

    private AnalysisResult analyzeTld(AnalyzerContext context) {
        try {
            Document document = SecureXml.parse(context.text());
            List<AnalysisFinding> output = new ArrayList<>();
            String uri = firstElementText(document, "uri");
            int uriLine = uri.isBlank()
                    ? findTagLine(context.text(), "taglib", 1)
                    : findLiteralLine(context.text(), uri, 1);
            context.ledger().recordConfiguration("CUSTOM_TAGS", "Custom JSP tags",
                    context.relativePath(), uriLine, "TLD-DECLARATION-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                    "JSP tag library descriptor identified.", "TLD-DECLARATION-001",
                    uriLine, Certainty.ANALYZED, ParseStatus.PARSED,
                    "{\"uri\":\"" + json(uri) + "\"}"));
            if (uri.toLowerCase(Locale.ROOT).contains("jstl")) {
                context.ledger().recordConfiguration("JSTL", "JSTL", context.relativePath(),
                        uriLine, "TLD-JSTL-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSTL",
                        "JSTL tag library descriptor identified.", "TLD-JSTL-001",
                        uriLine, Certainty.ANALYZED, ParseStatus.PARSED, null));
            }
            NodeList tags = document.getElementsByTagName("tag");
            for (int index = 0; index < tags.getLength(); index++) {
                Element tag = (Element) tags.item(index);
                String name = childText(tag, "name");
                int line = findLiteralLine(context.text(), "<name>" + name + "</name>", 1);
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                        "Custom tag definition" + (name.isBlank() ? "" : " " + name) + ".",
                        "TLD-TAG-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"tag\":\"" + json(name) + "\"}"));
            }
            return new AnalysisResult(List.copyOf(output), ParseStatus.PARSED, null);
        } catch (Exception exception) {
            return parseFailure(context, "TLD-PARSE-001", exception);
        }
    }

    private AnalysisResult analyzeXml(AnalyzerContext context) {
        try {
            Document document = SecureXml.parse(context.text());
            List<AnalysisFinding> output = new ArrayList<>();
            if (context.relativePath().endsWith("web.xml")) {
                output.addAll(analyzeWebXml(context, document));
            }
            if (context.relativePath().endsWith("struts-config.xml")) {
                output.addAll(analyzeStrutsConfig(context, document));
            }
            NodeList all = document.getElementsByTagName("*");
            Set<String> seen = new LinkedHashSet<>();
            for (int index = 0; index < all.getLength(); index++) {
                Element element = (Element) all.item(index);
                String className = firstNonBlank(element.getAttribute("class"),
                        element.getAttribute("type"));
                if (className.isBlank()) {
                    String childClass = childText(element, "class");
                    if (childClass.contains(".")) {
                        className = childClass;
                    }
                }
                if (className.isBlank() || !className.contains(".")) {
                    continue;
                }
                int line = findLiteralLine(context.text(), className, 1);
                if (!seen.add(className + ":" + line)) {
                    continue;
                }
                for (TechSignal signal : referenceSignals(className)) {
                    context.ledger().recordConfiguration(signal.key(), signal.name(),
                            context.relativePath(), line, "XML-CLASS-REF-001");
                    output.add(findings.create(context, AnalysisKind.CONFIGURATION, signal.key(),
                            "Configuration class reference to " + className + ".",
                            "XML-CLASS-REF-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                            "{\"className\":\"" + json(className) + "\"}"));
                }
                output.add(findings.create(context, AnalysisKind.CONFIGURATION,
                        "CLASS_REFERENCE", "Configuration references class " + className + ".",
                        "XML-CLASS-REF-002", line, Certainty.INFERRED, ParseStatus.PARSED, null));
            }
            return new AnalysisResult(List.copyOf(output), ParseStatus.PARSED, null);
        } catch (Exception exception) {
            return parseFailure(context, "XML-PARSE-001", exception);
        }
    }

    private List<AnalysisFinding> analyzeWebXml(AnalyzerContext context, Document document) {
        List<AnalysisFinding> output = new ArrayList<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (String tag : List.of("servlet", "filter", "listener")) {
            NodeList nodes = document.getElementsByTagName(tag);
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                String className = firstNonBlank(childText(element, "servlet-class"),
                        childText(element, "filter-class"), childText(element, "listener-class"));
                String marker = className.isBlank() ? "<" + tag : className;
                int occurrence = occurrences.merge(marker, 1, Integer::sum);
                int line = className.isBlank()
                        ? findTagLine(context.text(), tag, occurrence)
                        : findLiteralLine(context.text(), className, occurrence);
                context.ledger().recordConfiguration("SERVLET", "Servlet API", context.relativePath(),
                        line, "WEBXML-DECLARATION-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "SERVLET",
                        "web.xml " + tag + " declaration detected.",
                        "WEBXML-DECLARATION-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                        "{\"declaration\":\"" + tag + "\",\"className\":\""
                                + json(className) + "\"}"));
                for (TechSignal signal : referenceSignals(className)) {
                    context.ledger().recordConfiguration(signal.key(), signal.name(),
                            context.relativePath(), line, "WEBXML-CLASS-REF-001");
                    output.add(findings.create(context, AnalysisKind.CONFIGURATION, signal.key(),
                            "web.xml class reference belongs to " + signal.name() + ".",
                            "WEBXML-CLASS-REF-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                            null));
                }
            }
        }
        return output;
    }

    private List<AnalysisFinding> analyzeStrutsConfig(AnalyzerContext context, Document document) {
        List<AnalysisFinding> output = new ArrayList<>();
        NodeList messageResources = document.getElementsByTagName("message-resources");
        for (int index = 0; index < messageResources.getLength(); index++) {
            Element element = (Element) messageResources.item(index);
            String parameter = element.getAttribute("parameter").trim();
            int line = findTagLine(context.text(), "message-resources", index + 1);
            context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                    line, "STRUTS-CONFIG-MESSAGES-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                    "Struts message resources declaration" + (parameter.isBlank() ? "" : " " + parameter)
                            + " detected.",
                    "STRUTS-CONFIG-MESSAGES-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                    "{\"parameter\":\"" + json(parameter) + "\"}"));
        }
        NodeList formBeans = document.getElementsByTagName("form-bean");
        for (int index = 0; index < formBeans.getLength(); index++) {
            Element element = (Element) formBeans.item(index);
            String name = element.getAttribute("name").trim();
            String type = element.getAttribute("type").trim();
            int line = findTagLine(context.text(), "form-bean", index + 1);
            context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                    line, "STRUTS-CONFIG-FORM-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                    "Struts form bean declaration" + (name.isBlank() ? "" : " " + name) + " detected.",
                    "STRUTS-CONFIG-FORM-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                    "{\"name\":\"" + json(name) + "\",\"type\":\"" + json(type) + "\"}"));
        }
        NodeList actions = document.getElementsByTagName("action");
        int forwardIndex = 0;
        for (int index = 0; index < actions.getLength(); index++) {
            Element action = (Element) actions.item(index);
            String path = action.getAttribute("path").trim();
            String type = action.getAttribute("type").trim();
            String name = action.getAttribute("name").trim();
            String validate = action.getAttribute("validate").trim();
            int line = findTagLine(context.text(), "action", index + 1);
            context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                    line, "STRUTS-CONFIG-ACTION-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                    "Struts action mapping" + (path.isBlank() ? "" : " " + path)
                            + (type.isBlank() ? "" : " for " + type) + ".",
                    "STRUTS-CONFIG-ACTION-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                    "{\"path\":\"" + json(path) + "\",\"type\":\"" + json(type)
                            + "\",\"name\":\"" + json(name) + "\",\"validate\":\""
                            + json(validate) + "\"}"));
            for (Element forward : directChildren(action, "forward")) {
                forwardIndex++;
                String forwardName = forward.getAttribute("name").trim();
                String forwardPath = forward.getAttribute("path").trim();
                String redirect = forward.getAttribute("redirect").trim();
                int forwardLine = findTagLine(context.text(), "forward", forwardIndex);
                context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                        forwardLine, "STRUTS-CONFIG-FORWARD-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                        "Struts forward" + (forwardName.isBlank() ? "" : " " + forwardName)
                                + (forwardPath.isBlank() ? "" : " to " + forwardPath) + ".",
                        "STRUTS-CONFIG-FORWARD-001", forwardLine, Certainty.ANALYZED,
                        ParseStatus.PARSED,
                        "{\"actionPath\":\"" + json(path) + "\",\"name\":\"" + json(forwardName)
                                + "\",\"path\":\"" + json(forwardPath) + "\",\"redirect\":\""
                                + json(redirect) + "\"}"));
            }
        }
        NodeList plugins = document.getElementsByTagName("plug-in");
        for (int index = 0; index < plugins.getLength(); index++) {
            Element plugin = (Element) plugins.item(index);
            String className = plugin.getAttribute("className").trim();
            int line = findTagLine(context.text(), "plug-in", index + 1);
            context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                    line, "STRUTS-CONFIG-PLUGIN-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                    "Struts plug-in declaration" + (className.isBlank() ? "" : " " + className) + ".",
                    "STRUTS-CONFIG-PLUGIN-001", line, Certainty.ANALYZED, ParseStatus.PARSED,
                    "{\"className\":\"" + json(className) + "\"}"));
        }
        return output;
    }

    private AnalysisResult analyzeKeyValue(AnalyzerContext context) {
        List<AnalysisFinding> output = new ArrayList<>();
        List<String> lines = context.text().lines().toList();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String text = lines.get(index).strip();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("!")) {
                continue;
            }
            int line = index + 1;
            String className = null;
            Matcher matcher = PROPERTY_REFERENCE.matcher(text);
            if (matcher.find()) {
                className = matcher.group(1);
            } else {
                Matcher classMatcher = PROPERTY_ANY_CLASS.matcher(text);
                if (classMatcher.find()) {
                    className = classMatcher.group(1);
                }
            }
            if (className == null || !seen.add(className + ":" + line)) {
                continue;
            }
            for (TechSignal signal : referenceSignals(className)) {
                context.ledger().recordConfiguration(signal.key(), signal.name(), context.relativePath(),
                        line, "CONFIG-CLASS-REF-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, signal.key(),
                        "Configuration class reference belongs to " + signal.name() + ".",
                        "CONFIG-CLASS-REF-001", line, Certainty.INFERRED, ParseStatus.PARTIAL,
                        "{\"className\":\"" + json(className) + "\"}"));
            }
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CLASS_REFERENCE",
                    "Text-backed configuration references class " + className
                            + "; framework ownership requires confirmation.",
                    "CONFIG-CLASS-REF-002", line, Certainty.REVIEW_REQUIRED, ParseStatus.PARTIAL,
                    "{\"className\":\"" + json(className) + "\"}"));
        }
        return new AnalysisResult(List.copyOf(output), ParseStatus.PARTIAL, null);
    }

    private List<TechSignal> dependencySignals(String coordinate) {
        String lower = coordinate.toLowerCase(Locale.ROOT);
        List<TechSignal> signals = new ArrayList<>();
        if (lower.contains("sa-struts") || lower.contains("org.seasar.struts")) {
            signals.add(new TechSignal("SASTRUTS", "SAStruts"));
        }
        if (lower.contains("struts") && !lower.contains("sa-struts")) {
            signals.add(new TechSignal("STRUTS1", "Struts 1"));
        }
        if (lower.contains("seasar") && !lower.contains("seasar.struts")) {
            signals.add(new TechSignal("SEASAR2", "Seasar2"));
        }
        if (lower.contains("s2jdbc") || lower.contains("seasar.extension.jdbc")) {
            signals.add(new TechSignal("S2JDBC", "S2JDBC"));
        }
        if (lower.contains("servlet")) {
            signals.add(new TechSignal("SERVLET", "Servlet API"));
        }
        if (lower.contains("jstl")) {
            signals.add(new TechSignal("JSTL", "JSTL"));
        } else if (lower.contains("jsp")) {
            signals.add(new TechSignal("JSP", "JSP"));
        }
        if (lower.contains("junit")) {
            signals.add(new TechSignal("JUNIT", "JUnit"));
        }
        if (lower.contains("jdbc") || lower.contains("hibernate") || lower.contains("mybatis")
                || lower.contains("jpa")) {
            signals.add(new TechSignal("DB_ACCESS", "JDBC / ORM database access"));
        }
        return signals.stream().distinct().toList();
    }

    private List<TechSignal> referenceSignals(String className) {
        List<TechSignal> signals = new ArrayList<>();
        if (className == null) {
            return signals;
        }
        if (className.startsWith("org.seasar.struts")) {
            signals.add(new TechSignal("SASTRUTS", "SAStruts"));
        }
        if (className.startsWith("org.apache.struts")) {
            signals.add(new TechSignal("STRUTS1", "Struts 1"));
        }
        if (className.startsWith("org.seasar.framework") || className.startsWith("org.seasar.container")) {
            signals.add(new TechSignal("SEASAR2", "Seasar2"));
        }
        if (className.startsWith("org.seasar.extension.jdbc")) {
            signals.add(new TechSignal("S2JDBC", "S2JDBC"));
        }
        return signals;
    }

    private AnalysisResult parseFailure(AnalyzerContext context, String rule, Exception exception) {
        return new AnalysisResult(
                List.of(findings.parseFailure(context, rule, exception.getClass().getSimpleName(),
                        exception.getMessage())),
                ParseStatus.FAILED,
                exception.getMessage()
        );
    }

    private String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            String nodeName = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
            if (name.equals(nodeName)) {
                return node.getTextContent().trim();
            }
        }
        return "";
    }

    private String firstElementText(Document document, String name) {
        NodeList nodes = document.getElementsByTagNameNS("*", name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private int findTagLine(String text, String tagName, int occurrence) {
        if (tagName == null || tagName.isBlank() || occurrence <= 0) {
            return 1;
        }
        String token = "<" + tagName;
        int found = 0;
        int from = 0;
        while (from <= text.length() - token.length()) {
            int position = text.indexOf(token, from);
            if (position < 0) {
                break;
            }
            int after = position + token.length();
            char boundary = after < text.length() ? text.charAt(after) : '\0';
            if (boundary == '>' || boundary == '/' || Character.isWhitespace(boundary)) {
                found++;
                if (found == occurrence) {
                    return lineAt(text, position);
                }
            }
            from = after;
        }
        return 1;
    }

    private int findLiteralLine(String text, String token, int occurrence) {
        if (token == null || token.isBlank() || occurrence <= 0) {
            return 1;
        }
        int found = 0;
        int from = 0;
        while (from <= text.length() - token.length()) {
            int position = text.indexOf(token, from);
            if (position < 0) {
                break;
            }
            found++;
            if (found == occurrence) {
                return lineAt(text, position);
            }
            from = position + token.length();
        }
        return 1;
    }

    private int lineAt(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private Element directChild(Element parent, String localName) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && matches(element, localName)) {
                return element;
            }
        }
        return null;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && matches(element, localName)) {
                result.add(element);
            }
        }
        return result;
    }

    private boolean matches(Element element, String localName) {
        String name = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
        return localName.equals(name);
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record TechSignal(String key, String name) {
    }
}
