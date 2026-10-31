package dev.stravique.transformation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationArtifactType;
import dev.stravique.inventory.SecureXml;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.lang.model.SourceVersion;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DiconComponentSpringConfigRule implements TransformationRule {

    private static final String RULE_ID = "DICON-COMPONENT-SPRING-CONFIG";
    private static final String RULE_VERSION = "1.6.0";
    private static final Set<String> COMPONENT_ATTRIBUTES =
            Set.of("name", "class", "instance", "autoBinding");
    private static final Set<String> SCOPES =
            Set.of("prototype", "request", "session", "application");
    private static final Set<String> JAVA_LANG_TYPES =
            Set.of("String", "Boolean", "Integer", "Long", "Double");
    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+[lL]?");
    private static final Pattern DECIMAL_LITERAL = Pattern.compile("-?\\d+\\.\\d+");

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
        return MigrationItemType.CONFIGURATION;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_BOOT_CONFIG;
    }

    @Override
    public String language() {
        return "JAVA";
    }

    @Override
    public String fileNameHint() {
        return ".dicon";
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.CONFIGURATION
                && item.getTargetKey() == MigrationTarget.SPRING_BOOT_CONFIG
                && file.getFileType() == FileType.DICON;
    }

    @Override
    public TransformationDraft propose(TransformationRequest request) {
        Document document = parse(request.source());
        Element root = document.getDocumentElement();
        if (!"components".equals(localName(root))) {
            throw new TransformationUnsupportedException(
                    "Only a components dicon root is supported.");
        }
        Map<String, String> diconSources = diconSources(request.companions());
        String currentPath = normalizePath(request.relativePath());
        if (includedByOther(currentPath, diconSources)) {
            throw new TransformationUnsupportedException(
                    "A dicon included by another dicon is migrated from its root.");
        }
        List<ComponentSpec> components = components(root, currentPath, diconSources);
        if (components.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "The dicon does not declare a supported component.");
        }
        Map<String, ClassInfo> classes = classes(request.companions());
        List<ResolvedComponent> resolved = new ArrayList<>();
        Set<String> beanNames = new LinkedHashSet<>();
        for (ComponentSpec component : components) {
            resolveComponent(component, component.name(), classes, beanNames, resolved);
        }
        String diconRoot = moduleRoot(request.relativePath());
        String javaRoot = null;
        for (ResolvedComponent component : resolved) {
            String path = component.info().relativePath();
            int marker = path.indexOf("/src/main/java/");
            if (marker > 0 && diconRoot.equals(path.substring(0, marker))) {
                javaRoot = path.substring(0, marker + "/src/main/java/".length());
                break;
            }
        }
        if (javaRoot == null) {
            throw new TransformationUnsupportedException(
                    "No Java source in the dicon module can host the configuration.");
        }
        String commonPackage = commonPackage(
                resolved.stream().map(component -> component.info().packageName()).toList());
        if (commonPackage == null) {
            throw new TransformationUnsupportedException(
                    "The component classes do not share a package.");
        }
        String configurationName = configurationName(request.file().getFileName());
        if (!SourceVersion.isIdentifier(configurationName)
                || SourceVersion.isKeyword(configurationName)) {
            throw new TransformationUnsupportedException(
                    "The configuration class name is not a valid Java identifier.");
        }
        String targetPath = javaRoot + commonPackage.replace('.', '/') + "/"
                + configurationName + ".java";
        String proposed = configurationSource(commonPackage, configurationName, resolved);
        validate(proposed, targetPath);
        return new TransformationDraft(TransformationArtifactType.CREATE_FILE, targetPath,
                proposed);
    }

    private Map<String, String> diconSources(List<TransformationCompanion> companions) {
        Map<String, String> sources = new LinkedHashMap<>();
        for (TransformationCompanion companion : companions) {
            if (companion.relativePath().toLowerCase(Locale.ROOT).endsWith(".dicon")) {
                sources.put(normalizePath(companion.relativePath()), companion.source());
            }
        }
        return sources;
    }

    private boolean includedByOther(String currentPath, Map<String, String> diconSources) {
        for (Map.Entry<String, String> entry : diconSources.entrySet()) {
            if (entry.getKey().equals(currentPath)) {
                continue;
            }
            try {
                Element root = parse(entry.getValue()).getDocumentElement();
                if (!"components".equals(localName(root))) {
                    continue;
                }
                NodeList children = root.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    Node node = children.item(index);
                    if (node.getNodeType() != Node.ELEMENT_NODE
                            || !"include".equals(localName((Element) node))) {
                        continue;
                    }
                    String path = ((Element) node).getAttribute("path").trim();
                    if (!path.isBlank() && !Path.of(path).isAbsolute()
                            && normalizePath(Path.of(entry.getKey()).getParent()
                            .resolve(path).toString()).equals(currentPath)) {
                        return true;
                    }
                }
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return false;
    }

    private List<ComponentSpec> components(Element root, String currentPath,
                                           Map<String, String> diconSources) {
        List<ComponentSpec> components = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> included = new LinkedHashSet<>();
        visiting.add(currentPath);
        included.add(currentPath);
        collectComponents(root, currentPath, moduleRoot(currentPath), diconSources, visiting,
                included, components);
        return components;
    }

    private void collectComponents(Element root, String currentPath, String rootModule,
                                   Map<String, String> diconSources, Set<String> visiting,
                                   Set<String> included, List<ComponentSpec> components) {
        if (!"components".equals(localName(root))) {
            throw new TransformationUnsupportedException(
                    "An included dicon must have a components root.");
        }
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            if ("component".equals(localName(element))) {
                components.add(parseComponent(element));
                continue;
            }
            if (!"include".equals(localName(element))) {
                throw new TransformationUnsupportedException(
                        "Only direct component or include declarations are supported.");
            }
            requireAttributes(element, Set.of("path"), "include");
            if (hasElementChildren(element)) {
                throw new TransformationUnsupportedException(
                        "include content requires manual review.");
            }
            String path = element.getAttribute("path").trim();
            if (path.isBlank() || Path.of(path).isAbsolute()) {
                throw new TransformationUnsupportedException(
                        "A dicon include needs a relative path.");
            }
            String includedPath = normalizePath(
                    Path.of(currentPath).getParent().resolve(path).toString());
            if (!includedPath.startsWith(rootModule + "/")
                    || !moduleRoot(includedPath).equals(rootModule)) {
                throw new TransformationUnsupportedException(
                        "Cross-module dicon includes require manual review.");
            }
            if (visiting.contains(includedPath)) {
                throw new TransformationUnsupportedException(
                        "A dicon include cycle is not supported.");
            }
            if (included.contains(includedPath)) {
                continue;
            }
            String source = diconSources.get(includedPath);
            if (source == null) {
                throw new TransformationUnsupportedException(
                        "Included dicon " + path + " is not a project dicon.");
            }
            visiting.add(includedPath);
            collectComponents(parse(source).getDocumentElement(), includedPath, rootModule,
                    diconSources, visiting, included, components);
            visiting.remove(includedPath);
            included.add(includedPath);
        }
    }

    private ComponentSpec parseComponent(Element element) {
        requireAttributes(element, COMPONENT_ATTRIBUTES, "component");
        String name = element.getAttribute("name").trim();
        String className = element.getAttribute("class").trim();
        String instance = element.getAttribute("instance").trim();
        String autoBinding = element.getAttribute("autoBinding").trim();
        if (name.isBlank() || className.isBlank()) {
            throw new TransformationUnsupportedException(
                    "Every component needs an explicit name and class.");
        }
        if (!SourceVersion.isIdentifier(name) || SourceVersion.isKeyword(name)) {
            throw new TransformationUnsupportedException(
                    "Component name " + name + " is not a valid Java method name.");
        }
        String scope = null;
        if (!instance.isBlank() && !instance.equals("singleton")) {
            if (!SCOPES.contains(instance)) {
                throw new TransformationUnsupportedException(
                        "dicon instance " + instance + " requires manual review.");
            }
            scope = instance;
        }
        if (!autoBinding.isBlank()) {
            throw new TransformationUnsupportedException(
                    "dicon autoBinding requires manual review.");
        }
        ComponentBody body = parseBody(element);
        return new ComponentSpec(name, className, body.arguments(), body.properties(),
                body.initMethod(), scope);
    }

    private ComponentSpec parseNestedComponent(Element element) {
        requireAttributes(element, Set.of("class"), "nested component");
        String className = element.getAttribute("class").trim();
        if (className.isBlank()) {
            throw new TransformationUnsupportedException(
                    "A nested component needs an explicit class.");
        }
        ComponentBody body = parseBody(element);
        return new ComponentSpec(null, className, body.arguments(), body.properties(),
                body.initMethod(), null);
    }

    private ComponentBody parseBody(Element element) {
        List<PropertySpec> properties = new ArrayList<>();
        List<ArgumentSpec> arguments = new ArrayList<>();
        Set<String> propertyNames = new LinkedHashSet<>();
        String initMethod = null;
        NodeList componentChildren = element.getChildNodes();
        for (int childIndex = 0; childIndex < componentChildren.getLength(); childIndex++) {
            Node child = componentChildren.item(childIndex);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String childName = localName(childElement);
            if ("property".equals(childName)) {
                requireAttributes(childElement, Set.of("name"), "property");
                String propertyName = childElement.getAttribute("name").trim();
                if (!SourceVersion.isIdentifier(propertyName)
                        || SourceVersion.isKeyword(propertyName)) {
                    throw new TransformationUnsupportedException(
                            "Property name " + propertyName + " is not a valid Java identifier.");
                }
                if (!propertyNames.add(propertyName)) {
                    throw new TransformationUnsupportedException(
                            "Duplicate property " + propertyName + ".");
                }
                List<Element> nestedChildren = elementChildren(childElement);
                if (nestedChildren.isEmpty()) {
                    String value = childElement.getTextContent().trim();
                    if (value.isBlank()) {
                        throw new TransformationUnsupportedException(
                                "Only a named simple property value is supported.");
                    }
                    properties.add(new PropertySpec(propertyName, value, null));
                } else if (nestedChildren.size() == 1
                        && "component".equals(localName(nestedChildren.get(0)))) {
                    if (hasDirectText(childElement)) {
                        throw new TransformationUnsupportedException(
                                "A nested component property cannot also have a value.");
                    }
                    properties.add(new PropertySpec(propertyName, null,
                            parseNestedComponent(nestedChildren.get(0))));
                } else {
                    throw new TransformationUnsupportedException(
                            "A property requires one anonymous component or a simple value.");
                }
            } else if ("arg".equals(childName)) {
                requireAttributes(childElement, Set.of(), "arg");
                List<Element> nestedChildren = elementChildren(childElement);
                if (nestedChildren.isEmpty()) {
                    String value = childElement.getTextContent().trim();
                    if (value.isBlank()) {
                        throw new TransformationUnsupportedException(
                                "A constructor argument needs a value.");
                    }
                    arguments.add(new ArgumentSpec(value, null));
                } else if (nestedChildren.size() == 1
                        && "component".equals(localName(nestedChildren.get(0)))) {
                    if (hasDirectText(childElement)) {
                        throw new TransformationUnsupportedException(
                                "A nested component argument cannot also have a value.");
                    }
                    arguments.add(new ArgumentSpec(null,
                            parseNestedComponent(nestedChildren.get(0))));
                } else {
                    throw new TransformationUnsupportedException(
                            "An argument requires one anonymous component or a simple value.");
                }
            } else if ("initMethod".equals(childName)) {
                requireAttributes(childElement, Set.of("name"), "initMethod");
                if (hasElementChildren(childElement)) {
                    throw new TransformationUnsupportedException(
                            "initMethod content requires manual review.");
                }
                String methodName = childElement.getAttribute("name").trim();
                if (methodName.isBlank() || !SourceVersion.isIdentifier(methodName)
                        || SourceVersion.isKeyword(methodName) || initMethod != null) {
                    throw new TransformationUnsupportedException(
                            "Only one simple initMethod is supported.");
                }
                initMethod = methodName;
            } else {
                throw new TransformationUnsupportedException(
                        "Component element " + childName + " requires manual review.");
            }
        }
        return new ComponentBody(arguments, properties, initMethod);
    }

    private ResolvedComponent resolveComponent(ComponentSpec component, String beanName,
                                               Map<String, ClassInfo> classes,
                                               Set<String> beanNames,
                                               List<ResolvedComponent> resolved) {
        if (!beanNames.add(beanName)) {
            throw new TransformationUnsupportedException(
                    "Duplicate component name " + beanName + ".");
        }
        ClassInfo info = classes.get(component.className());
        if (info == null) {
            throw new TransformationUnsupportedException(
                    "Component class " + component.className()
                            + " is not a project Java source.");
        }
        requireInstantiable(info, component.arguments().size());
        List<BoundArgument> arguments = new ArrayList<>();
        if (!component.arguments().isEmpty()) {
            ConstructorDeclaration constructor = info.declaration().getConstructors().get(0);
            for (int index = 0; index < component.arguments().size(); index++) {
                ArgumentSpec argument = component.arguments().get(index);
                String type = resolveType(info, constructor.getParameter(index).getType());
                if (argument.nested() == null) {
                    arguments.add(new BoundArgument(type,
                            bindValue(type, argument.value(), "argument")));
                    continue;
                }
                ResolvedComponent nested = resolveComponent(argument.nested(),
                        beanName + "Arg" + (index + 1), classes, beanNames, resolved);
                if (!type.equals(nested.info().fqcn())) {
                    throw new TransformationUnsupportedException(
                            "Nested argument " + (index + 1) + " for " + beanName
                                    + " does not match " + type + ".");
                }
                arguments.add(new BoundArgument(type,
                        new ValueSpec(nested.beanName(), nested.beanName())));
            }
        }
        List<BoundProperty> properties = new ArrayList<>();
        for (PropertySpec property : component.properties()) {
            ResolvedComponent nested = null;
            if (property.nested() != null) {
                nested = resolveComponent(property.nested(),
                        beanName + capitalize(property.name()), classes, beanNames, resolved);
            }
            properties.add(bindProperty(info, property, nested));
        }
        String initMethod = component.initMethod();
        if (initMethod != null) {
            requireInitMethod(info, initMethod);
        }
        ResolvedComponent result = new ResolvedComponent(beanName, info, arguments, properties,
                initMethod, component.scope());
        resolved.add(result);
        return result;
    }

    private BoundProperty bindProperty(ClassInfo info, PropertySpec property,
                                       ResolvedComponent nested) {
        String setterName = "set" + capitalize(property.name());
        MethodDeclaration setter = info.declaration().getMethods().stream()
                .filter(method -> method.hasModifier(Modifier.Keyword.PUBLIC))
                .filter(method -> !method.isStatic())
                .filter(method -> method.getNameAsString().equals(setterName))
                .filter(method -> method.getParameters().size() == 1)
                .findFirst()
                .orElse(null);
        if (setter != null) {
            String type = resolveType(info, setter.getParameter(0).getType());
            return boundProperty(property, setterName, type, nested);
        }
        FieldDeclaration field = info.declaration().getFields().stream()
                .filter(candidate -> candidate.hasModifier(Modifier.Keyword.PUBLIC))
                .filter(candidate -> !candidate.isStatic())
                .filter(candidate -> candidate.getVariables().stream()
                        .anyMatch(variable -> variable.getNameAsString()
                                .equals(property.name())))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "Property " + property.name() + " has no public field or setter."));
        Type type = field.getVariable(0).getType();
        String resolved = resolveType(info, type);
        return boundProperty(property, null, resolved, nested);
    }

    private BoundProperty boundProperty(PropertySpec property, String setterName, String type,
                                        ResolvedComponent nested) {
        if (nested != null) {
            if (!type.equals(nested.info().fqcn())) {
                throw new TransformationUnsupportedException(
                        "Nested component for property " + property.name()
                                + " does not match " + type + ".");
            }
            return new BoundProperty(property.name(), setterName, type,
                    new ValueSpec(nested.beanName(), nested.beanName()));
        }
        return new BoundProperty(property.name(), setterName, type,
                bindValue(type, property.value(), "property"));
    }

    private String resolveType(ClassInfo info, Type type) {
        if (type.isArrayType() || type.isWildcardType()
                || type.isVoidType() || type.asString().contains("<")) {
            throw new TransformationUnsupportedException(
                    "Property type " + type.asString() + " requires manual review.");
        }
        String text = type.asString();
        if (type.isPrimitiveType() || text.contains(".") || JAVA_LANG_TYPES.contains(text)) {
            return text;
        }
        String imported = info.unit().getImports().stream()
                .filter(declaration -> !declaration.isAsterisk())
                .map(declaration -> declaration.getNameAsString())
                .filter(name -> name.endsWith("." + text))
                .findFirst()
                .orElse(null);
        if (imported != null) {
            return imported;
        }
        if (info.packageName() != null && !info.packageName().isBlank()) {
            return info.packageName() + "." + text;
        }
        throw new TransformationUnsupportedException(
                "Property type " + text + " cannot be resolved.");
    }

    private void requireInitMethod(ClassInfo info, String initMethod) {
        boolean exists = info.declaration().getMethods().stream()
                .anyMatch(method -> method.getNameAsString().equals(initMethod)
                        && method.getParameters().isEmpty()
                        && method.hasModifier(Modifier.Keyword.PUBLIC)
                        && !method.isStatic());
        if (!exists) {
            throw new TransformationUnsupportedException(
                    "Init method " + initMethod + " is not a public no-arg method.");
        }
    }

    private Map<String, ClassInfo> classes(List<TransformationCompanion> companions) {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        for (TransformationCompanion companion : companions) {
            if (!companion.relativePath().endsWith(".java")) {
                continue;
            }
            try {
                CompilationUnit unit = StaticJavaParser.parse(companion.source());
                ClassOrInterfaceDeclaration declaration = unit
                        .findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
                if (declaration == null || declaration.isInterface()) {
                    continue;
                }
                String packageName = unit.getPackageDeclaration()
                        .map(packageDeclaration -> packageDeclaration.getNameAsString())
                        .orElse("");
                if (packageName.isBlank()) {
                    continue;
                }
                String fqcn = packageName + "." + declaration.getNameAsString();
                classes.put(fqcn, new ClassInfo(fqcn, packageName,
                        declaration.getNameAsString(), companion.relativePath(), unit,
                        declaration));
            } catch (RuntimeException ignored) {
                continue;
            }
        }
        return classes;
    }

    private void requireInstantiable(ClassInfo info, int argumentCount) {
        ClassOrInterfaceDeclaration declaration = info.declaration();
        if (!declaration.hasModifier(Modifier.Keyword.PUBLIC) || declaration.isAbstract()) {
            throw new TransformationUnsupportedException(
                    "Component class " + info.fqcn() + " must be a public concrete class.");
        }
        List<ConstructorDeclaration> constructors = declaration.getConstructors();
        if (argumentCount == 0) {
            if (constructors.isEmpty()) {
                return;
            }
            if (constructors.size() != 1) {
                throw new TransformationUnsupportedException(
                        "Component class " + info.fqcn() + " needs a single no-arg constructor.");
            }
            ConstructorDeclaration constructor = constructors.get(0);
            if (constructor.getParameters().size() != 0 || !constructor.isPublic()) {
                throw new TransformationUnsupportedException(
                        "Component class " + info.fqcn() + " needs a public no-arg constructor.");
            }
            return;
        }
        if (constructors.size() != 1) {
            throw new TransformationUnsupportedException(
                    "Component class " + info.fqcn()
                            + " needs one unambiguous constructor for its arguments.");
        }
        ConstructorDeclaration constructor = constructors.get(0);
        if (!constructor.isPublic()
                || constructor.getParameters().size() != argumentCount) {
            throw new TransformationUnsupportedException(
                    "Component class " + info.fqcn()
                            + " does not expose a public constructor matching its arguments.");
        }
    }

    private ValueSpec bindValue(String typeName, String raw, String kind) {
        String simple = simpleType(typeName);
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            String content = raw.substring(1, raw.length() - 1);
            if (content.contains("\\") || content.contains("\"")
                    || content.contains("\n") || content.contains("\r")) {
                throw new TransformationUnsupportedException(
                        "Escaped string " + kind + " values require manual review.");
            }
            if (!simple.equals("String")) {
                throw new TransformationUnsupportedException(
                        "String " + kind + " " + raw + " does not match " + typeName + ".");
            }
            return new ValueSpec(null, "\"" + content + "\"");
        }
        if (raw.equals("true") || raw.equals("false")) {
            if (!simple.equals("boolean") && !simple.equals("Boolean")) {
                throw new TransformationUnsupportedException(
                        "Boolean " + kind + " " + raw + " does not match " + typeName + ".");
            }
            return new ValueSpec(null, raw);
        }
        if (INTEGER_LITERAL.matcher(raw).matches()) {
            boolean hasLongSuffix = raw.endsWith("L") || raw.endsWith("l");
            String digits = hasLongSuffix ? raw.substring(0, raw.length() - 1) : raw;
            try {
                long parsed = Long.parseLong(digits);
                if (simple.equals("int") || simple.equals("Integer")) {
                    if (hasLongSuffix || parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                        throw new TransformationUnsupportedException(
                                "Integer " + kind + " " + raw + " is out of range.");
                    }
                    return new ValueSpec(null, digits);
                }
                if (simple.equals("long") || simple.equals("Long")) {
                    return new ValueSpec(null, parsed + "L");
                }
                if (simple.equals("double") || simple.equals("Double")) {
                    if (hasLongSuffix) {
                        throw new TransformationUnsupportedException(
                                "Long " + kind + " " + raw + " does not match " + typeName + ".");
                    }
                    return new ValueSpec(null, digits);
                }
            } catch (NumberFormatException exception) {
                throw new TransformationUnsupportedException(
                        "Integer " + kind + " " + raw + " is out of range.");
            }
            throw new TransformationUnsupportedException(
                    "Integer " + kind + " " + raw + " does not match " + typeName + ".");
        }
        if (DECIMAL_LITERAL.matcher(raw).matches()) {
            if (!simple.equals("double") && !simple.equals("Double")) {
                throw new TransformationUnsupportedException(
                        "Decimal " + kind + " " + raw + " does not match " + typeName + ".");
            }
            double parsed = Double.parseDouble(raw);
            if (!Double.isFinite(parsed)) {
                throw new TransformationUnsupportedException(
                        "Decimal " + kind + " " + raw + " is out of range.");
            }
            return new ValueSpec(null, raw);
        }
        if (SourceVersion.isIdentifier(raw) && !SourceVersion.isKeyword(raw)) {
            if (typeName.equals("boolean") || typeName.equals("int") || typeName.equals("long")
                    || typeName.equals("double")) {
                throw new TransformationUnsupportedException(
                        "A primitive " + kind + " cannot reference a component.");
            }
            return new ValueSpec(raw, raw);
        }
        throw new TransformationUnsupportedException(
                "The " + kind + " value " + raw + " is not a supported literal or reference.");
    }

    private String simpleType(String typeName) {
        int separator = typeName.lastIndexOf('.');
        return separator < 0 ? typeName : typeName.substring(separator + 1);
    }

    private String moduleRoot(String relativePath) {
        int marker = relativePath.indexOf("/src/");
        if (marker <= 0) {
            throw new TransformationUnsupportedException(
                    "The dicon is not under a recognized module source root.");
        }
        return relativePath.substring(0, marker);
    }

    private String normalizePath(String relativePath) {
        try {
            return Path.of(relativePath).normalize().toString().replace('\\', '/');
        } catch (InvalidPathException exception) {
            throw new TransformationUnsupportedException(
                    "The dicon path is not a valid relative path.");
        }
    }

    private String commonPackage(List<String> packages) {
        List<String[]> split = packages.stream()
                .map(value -> value.split("\\."))
                .toList();
        int limit = split.stream().mapToInt(parts -> parts.length).min().orElse(0);
        List<String> common = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            String segment = split.get(0)[index];
            for (String[] parts : split) {
                if (!parts[index].equals(segment)) {
                    return common.isEmpty() ? null : String.join(".", common);
                }
            }
            common.add(segment);
        }
        return common.isEmpty() ? null : String.join(".", common);
    }

    private String configurationName(String fileName) {
        String base = fileName.toLowerCase(Locale.ROOT).endsWith(".dicon")
                ? fileName.substring(0, fileName.length() - ".dicon".length()) : fileName;
        StringBuilder builder = new StringBuilder();
        for (String part : base.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }
        if (builder.isEmpty()) {
            builder.append("Dicon");
        }
        return builder + "DiconConfiguration";
    }

    private String configurationSource(String packageName, String configurationName,
                                       List<ResolvedComponent> components) {
        boolean hasReferences = components.stream()
                .anyMatch(component -> component.arguments().stream()
                                .anyMatch(argument -> argument.value().reference() != null)
                        || component.properties().stream()
                                .anyMatch(property -> property.value().reference() != null));
        boolean hasScopes = components.stream()
                .anyMatch(component -> component.scope() != null);
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        if (hasReferences) {
            source.append("import org.springframework.beans.factory.annotation.Qualifier;\n");
        }
        source.append("import org.springframework.context.annotation.Bean;\n")
                .append("import org.springframework.context.annotation.Configuration;\n");
        if (hasScopes) {
            source.append("import org.springframework.context.annotation.Scope;\n");
        }
        source.append("\n@Configuration\n")
                .append("public class ").append(configurationName).append(" {\n");
        for (ResolvedComponent component : components) {
            source.append("\n    @Bean\n");
            if (component.scope() != null) {
                source.append("    @Scope(\"").append(component.scope()).append("\")\n");
            }
            source.append("    public ").append(component.info().fqcn()).append(" ")
                    .append(component.beanName()).append("(");
            Map<String, String> parameters = new LinkedHashMap<>();
            for (BoundArgument argument : component.arguments()) {
                if (argument.value().reference() != null) {
                    mergeParameter(parameters, argument.value().reference(),
                            argument.typeName());
                }
            }
            for (BoundProperty property : component.properties()) {
                if (property.value().reference() != null) {
                    mergeParameter(parameters, property.value().reference(),
                            property.typeName());
                }
            }
            source.append(String.join(", ", parameters.entrySet().stream()
                    .map(entry -> "@Qualifier(\"" + entry.getKey() + "\") "
                            + entry.getValue() + " " + entry.getKey())
                    .toList()));
            source.append(") {\n");
            String constructor = "new " + component.info().fqcn() + "("
                    + String.join(", ", component.arguments().stream()
                    .map(argument -> argument.value().expression())
                    .toList()) + ")";
            if (component.properties().isEmpty() && component.initMethod() == null) {
                source.append("        return ").append(constructor).append(";\n");
            } else {
                String local = parameters.containsKey("bean") ? "componentBean" : "bean";
                source.append("        ").append(component.info().fqcn()).append(" ")
                        .append(local).append(" = ").append(constructor).append(";\n");
                for (BoundProperty property : component.properties()) {
                    if (property.setterName() == null) {
                        source.append("        ").append(local).append(".")
                                .append(property.fieldName()).append(" = ")
                                .append(property.value().expression()).append(";\n");
                    } else {
                        source.append("        ").append(local).append(".")
                                .append(property.setterName()).append("(")
                                .append(property.value().expression()).append(");\n");
                    }
                }
                if (component.initMethod() != null) {
                    source.append("        ").append(local).append(".")
                            .append(component.initMethod()).append("();\n");
                }
                source.append("        return ").append(local).append(";\n");
            }
            source.append("    }\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private void mergeParameter(Map<String, String> parameters, String reference, String type) {
        String existing = parameters.get(reference);
        if (existing != null && !existing.equals(type)) {
            throw new TransformationUnsupportedException(
                    "Reference " + reference + " has conflicting types.");
        }
        parameters.putIfAbsent(reference, type);
    }

    private void requireAttributes(Element element, Set<String> allowed, String kind) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            String name = attributes.item(index).getNodeName();
            if (!allowed.contains(name)) {
                throw new TransformationUnsupportedException(
                        kind + " attribute " + name + " requires manual review.");
            }
        }
    }

    private boolean hasElementChildren(Element element) {
        return !elementChildren(element).isEmpty();
    }

    private List<Element> elementChildren(Element element) {
        List<Element> elements = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index).getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) children.item(index));
            }
        }
        return elements;
    }

    private boolean hasDirectText(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if ((child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !child.getTextContent().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private Document parse(String text) {
        try {
            return SecureXml.parse(text);
        } catch (Exception exception) {
            throw new TransformationInvalidException(
                    "The dicon is not well-formed XML.");
        }
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private record ComponentSpec(String name, String className, List<ArgumentSpec> arguments,
                                 List<PropertySpec> properties, String initMethod,
                                 String scope) {
    }

    private record ComponentBody(List<ArgumentSpec> arguments, List<PropertySpec> properties,
                                 String initMethod) {
    }

    private record ArgumentSpec(String value, ComponentSpec nested) {
    }

    private record PropertySpec(String name, String value, ComponentSpec nested) {
    }

    private record ValueSpec(String reference, String expression) {
    }

    private record BoundArgument(String typeName, ValueSpec value) {
    }

    private record ClassInfo(String fqcn, String packageName, String simpleName,
                             String relativePath, CompilationUnit unit,
                             ClassOrInterfaceDeclaration declaration) {
    }

    private record BoundProperty(String fieldName, String setterName, String typeName,
                                 ValueSpec value) {
    }

    private record ResolvedComponent(String beanName, ClassInfo info,
                                     List<BoundArgument> arguments,
                                     List<BoundProperty> properties, String initMethod,
                                     String scope) {
    }
}
