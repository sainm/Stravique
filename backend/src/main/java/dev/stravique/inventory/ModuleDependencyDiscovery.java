package dev.stravique.inventory;

import dev.stravique.domain.Certainty;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ModuleDependencyType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ModuleDependencyDiscovery {

    private static final Pattern GRADLE_PROJECT = Pattern.compile(
            "(?i)project\\s*\\(\\s*(?:path\\s*:\\s*)?['\"]?(:[A-Za-z0-9_:.-]+)['\"]?"
    );

    private final TextDecoder decoder;

    public ModuleDependencyDiscovery(TextDecoder decoder) {
        this.decoder = decoder;
    }

    public List<DeclaredDependency> discover(Path root, ModuleDiscovery.ModuleLayout layout,
                                             TreeWalker.WalkResult walk) throws IOException {
        List<DeclaredDependency> result = new ArrayList<>();
        Map<String, ModuleCoordinates> coordinates = new LinkedHashMap<>();

        for (ModuleDiscovery.ModuleSnapshot module : layout.modules()) {
            Path pom = modulePath(root, module.relativePath()).resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                PomCoordinates pomCoordinates = readPom(pom);
                if (pomCoordinates.artifactId != null) {
                    coordinates.put(normalize(module.relativePath()),
                            new ModuleCoordinates(normalize(module.relativePath()),
                                    pomCoordinates.groupId, pomCoordinates.artifactId));
                }
            }
        }

        for (ModuleDiscovery.ModuleSnapshot module : layout.modules()) {
            Path pom = modulePath(root, module.relativePath()).resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                result.addAll(readMavenDependencies(root, module, pom, coordinates));
            }
        }

        for (TreeWalker.DiscoveredFile file : walk.files()) {
            if (file.fileType() != FileType.GRADLE || !file.relativePath().endsWith(".gradle")
                    && !file.relativePath().endsWith(".gradle.kts")) {
                continue;
            }
            ModuleDiscovery.ModuleSnapshot source = layout.moduleFor(file.relativePath());
            TextDecoder.DecodedSource decoded = decoder.decode(file.absolutePath());
            List<String> lines = decoded.text().lines().toList();
            Set<String> seen = new LinkedHashSet<>();
            for (int index = 0; index < lines.size(); index++) {
                Matcher matcher = GRADLE_PROJECT.matcher(lines.get(index));
                while (matcher.find()) {
                    String target = normalizeModule(matcher.group(1));
                    if (target.equals(normalize(source.relativePath()))
                            || layout.modules().stream().noneMatch(module ->
                            normalize(module.relativePath()).equals(target))) {
                        continue;
                    }
                    String unique = target + ":" + (index + 1);
                    if (seen.add(unique)) {
                        result.add(new DeclaredDependency(
                                normalize(source.relativePath()),
                                target,
                                ModuleDependencyType.GRADLE_PROJECT,
                                file.relativePath(),
                                index + 1,
                                "GRADLE-MODULE-DEPENDENCY-001",
                                Certainty.ANALYZED
                        ));
                    }
                }
            }
        }

        return result.stream().distinct().toList();
    }

    private List<DeclaredDependency> readMavenDependencies(
            Path root,
            ModuleDiscovery.ModuleSnapshot source,
            Path pom,
            Map<String, ModuleCoordinates> coordinates
    ) throws IOException {
        try {
            TextDecoder.DecodedSource decoded = decoder.decode(pom);
            Document document = SecureXml.parse(decoded.text());
            Element project = document.getDocumentElement();
            Element dependencies = directChild(project, "dependencies");
            if (dependencies == null) {
                return List.of();
            }
            String evidencePath = root.relativize(pom).toString().replace('\\', '/');
            List<DeclaredDependency> result = new ArrayList<>();
            for (Element dependency : directChildren(dependencies, "dependency")) {
                String groupId = directText(dependency, "groupId");
                String artifactId = directText(dependency, "artifactId");
                if (artifactId.isBlank()) {
                    continue;
                }
                for (ModuleCoordinates target : coordinates.values()) {
                    if (!target.artifactId().equals(artifactId)
                            || target.path().equals(normalize(source.relativePath()))) {
                        continue;
                    }
                    if (!groupId.isBlank() && !target.groupId().isBlank()
                            && !target.groupId().equals(groupId)) {
                        continue;
                    }
                    int line = findLine(decoded.text(), artifactId);
                    result.add(new DeclaredDependency(
                            normalize(source.relativePath()),
                            target.path(),
                            ModuleDependencyType.MAVEN_MODULE,
                            evidencePath,
                            line,
                            "MAVEN-MODULE-DEPENDENCY-001",
                            Certainty.ANALYZED
                    ));
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private PomCoordinates readPom(Path pom) throws IOException {
        try {
            Document document = SecureXml.parse(decoder.decode(pom).text());
            Element project = document.getDocumentElement();
            String groupId = directText(project, "groupId");
            if (groupId.isBlank()) {
                Element parent = directChild(project, "parent");
                groupId = parent == null ? "" : directText(parent, "groupId");
            }
            return new PomCoordinates(groupId, directText(project, "artifactId"));
        } catch (Exception exception) {
            return new PomCoordinates("", null);
        }
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

    private String directText(Element parent, String localName) {
        Element child = directChild(parent, localName);
        return child == null ? "" : child.getTextContent().trim();
    }

    private boolean matches(Element element, String localName) {
        String name = element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
        return localName.equals(name);
    }

    private int findLine(String text, String artifactId) {
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains(artifactId)) {
                return index + 1;
            }
        }
        return 1;
    }

    private Path modulePath(Path root, String relativePath) {
        return normalize(relativePath).equals(".")
                ? root : root.resolve(relativePath).normalize();
    }

    private String normalizeModule(String gradlePath) {
        String value = gradlePath.replace(':', '/').replaceAll("^/+", "");
        return value.isBlank() ? "." : value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "." : value;
    }

    public record DeclaredDependency(
            String fromModulePath,
            String toModulePath,
            ModuleDependencyType type,
            String evidencePath,
            Integer lineStart,
            String ruleId,
            Certainty certainty
    ) {
    }

    private record ModuleCoordinates(String path, String groupId, String artifactId) {
    }

    private record PomCoordinates(String groupId, String artifactId) {
    }
}
