package dev.stravique.inventory;

import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.BuildTool;
import dev.stravique.domain.ProjectType;
import dev.stravique.domain.PathOverride;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ModuleDiscovery {

    private static final Pattern GRADLE_QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");

    private final TextDecoder decoder;
    private final StraviqueProperties properties;

    public ModuleDiscovery(TextDecoder decoder, StraviqueProperties properties) {
        this.decoder = decoder;
        this.properties = properties;
    }

    public ModuleLayout discover(Path root, TreeWalker.WalkResult walk,
                                 List<PathOverride> overrides) throws IOException {
        Set<String> modulePaths = new LinkedHashSet<>();
        modulePaths.add("");

        for (TreeWalker.DiscoveredFile file : walk.files()) {
            if (file.fileType() == dev.stravique.domain.FileType.MAVEN_POM) {
                Path parent = file.absolutePath().getParent();
                String relative = root.relativize(parent).toString().replace('\\', '/');
                modulePaths.add(relative);
            }
        }

        Path rootPom = root.resolve("pom.xml");
        if (Files.isRegularFile(rootPom)) {
            modulePaths.addAll(readPomModules(root, rootPom));
        }
        for (String settingsName : List.of("settings.gradle", "settings.gradle.kts")) {
            Path settings = root.resolve(settingsName);
            if (Files.isRegularFile(settings)) {
                modulePaths.addAll(readGradleModules(root, settings));
            }
        }

        List<ModuleSnapshot> modules = new ArrayList<>();
        for (String relative : modulePaths.stream().filter(Objects::nonNull).sorted().toList()) {
            Path moduleRoot = relative.isBlank() ? root : root.resolve(relative).normalize();
            if (!moduleRoot.startsWith(root) || !Files.isDirectory(moduleRoot)) {
                continue;
            }
            modules.add(snapshot(root, moduleRoot, relative, overrides));
        }
        if (modules.isEmpty()) {
            modules.add(snapshot(root, root, "", overrides));
        }

        boolean hasMaven = walk.files().stream()
                .anyMatch(file -> file.fileType() == dev.stravique.domain.FileType.MAVEN_POM);
        boolean hasGradle = walk.files().stream()
                .anyMatch(file -> file.fileType() == dev.stravique.domain.FileType.GRADLE);
        BuildTool buildTool = hasMaven && hasGradle ? BuildTool.MIXED
                : hasMaven ? BuildTool.MAVEN
                : hasGradle ? BuildTool.GRADLE
                : BuildTool.UNKNOWN;
        boolean multiModule = modules.size() > 1;
        ProjectType projectType = switch (buildTool) {
            case MAVEN -> multiModule ? ProjectType.MAVEN_MULTI_MODULE : ProjectType.MAVEN_SINGLE_MODULE;
            case GRADLE, MIXED -> multiModule ? ProjectType.GRADLE_MULTI_MODULE : ProjectType.GRADLE_SINGLE_MODULE;
            case UNKNOWN -> hasEclipseWebRoot(root) ? ProjectType.ECLIPSE_WEB : ProjectType.UNKNOWN;
        };

        return new ModuleLayout(List.copyOf(modules), buildTool, projectType, detectJdkVersion(root, walk));
    }

    private ModuleSnapshot snapshot(Path root, Path moduleRoot, String relative,
                                    List<PathOverride> overrides) throws IOException {
        List<String> sourceRoots = conventionalRoots(root, moduleRoot, List.of("src/main/java"));
        List<String> testRoots = conventionalRoots(root, moduleRoot, List.of("src/test/java", "test"));
        List<String> resourceRoots = conventionalRoots(root, moduleRoot, List.of("src/main/resources", "resources"));
        List<String> webRoots = conventionalRoots(root, moduleRoot,
                List.of("src/main/webapp", "web", "WebContent", "src/main/web"));
        List<String> configRoots = conventionalRoots(root, moduleRoot, List.of("config", "conf", "WEB-INF/conf"));
        List<String> sqlRoots = conventionalRoots(root, moduleRoot, List.of("sql", "db", "src/main/resources/sql"));

        for (PathOverride override : overrides) {
            String overridePath = override.getRelativePath().equals(".") ? "" : override.getRelativePath();
            boolean belongs = overridePath.isEmpty() || overridePath.equals(relative)
                    || overridePath.startsWith(relative + "/") || relative.isEmpty();
            if (!belongs) {
                continue;
            }
            Path candidate = root.resolve(overridePath).normalize();
            if (!Files.isDirectory(candidate)) {
                continue;
            }
            List<String> target = switch (override.getRole()) {
                case JAVA_SOURCE -> sourceRoots;
                case TEST_SOURCE -> testRoots;
                case RESOURCE -> resourceRoots;
                case WEB_ROOT, JSP_ROOT, STATIC_RESOURCE -> webRoots;
                case CONFIGURATION -> configRoots;
                case SQL -> sqlRoots;
            };
            target.add(overridePath);
        }

        String name = relative.isBlank() ? root.getFileName().toString()
                : Path.of(relative).getFileName().toString();
        return new ModuleSnapshot(relative, name,
                deduplicate(sourceRoots), deduplicate(testRoots), deduplicate(resourceRoots),
                deduplicate(webRoots), deduplicate(configRoots), deduplicate(sqlRoots));
    }

    private List<String> conventionalRoots(Path root, Path moduleRoot, List<String> candidates) {
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            Path absolute = moduleRoot.resolve(candidate);
            if (Files.isDirectory(absolute)) {
                result.add(root.relativize(absolute).toString().replace('\\', '/'));
            }
        }
        return result;
    }

    private List<String> deduplicate(Collection<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<String> readPomModules(Path root, Path pom) {
        List<String> modules = new ArrayList<>();
        try {
            TextDecoder.DecodedSource decoded = decoder.decode(pom);
            Document document = SecureXml.parse(decoded.text());
            NodeList nodes = document.getElementsByTagName("module");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element element = (Element) nodes.item(index);
                String relative = root.relativize(root.resolve(element.getTextContent().trim()).normalize())
                        .toString().replace('\\', '/');
                modules.add(relative);
            }
        } catch (Exception ignored) {
            // Parse errors are recorded by the file analyzer.
        }
        return modules;
    }

    private List<String> readGradleModules(Path root, Path settings) {
        List<String> modules = new ArrayList<>();
        try {
            TextDecoder.DecodedSource decoded = decoder.decode(settings);
            for (String line : decoded.text().lines().toList()) {
                String trimmed = line.strip();
                if (!(trimmed.startsWith("include") || trimmed.startsWith("includes"))) {
                    continue;
                }
                Matcher matcher = GRADLE_QUOTED.matcher(trimmed);
                while (matcher.find()) {
                    String project = matcher.group(1).replace(":", "/").replaceAll("^/+", "");
                    if (!project.isBlank() && !project.startsWith("..")) {
                        modules.add(project);
                    }
                }
            }
        } catch (IOException ignored) {
            // Settings parse errors are recorded by the file analyzer.
        }
        return modules;
    }

    private boolean hasEclipseWebRoot(Path root) throws IOException {
        return Files.isDirectory(root.resolve("WebContent"))
                || Files.isDirectory(root.resolve("web"))
                || Files.isDirectory(root.resolve("WEB-INF"));
    }

    private String detectJdkVersion(Path root, TreeWalker.WalkResult walk) throws IOException {
        for (TreeWalker.DiscoveredFile file : walk.files()) {
            if (file.fileType() != dev.stravique.domain.FileType.MAVEN_POM
                    && file.fileType() != dev.stravique.domain.FileType.GRADLE) {
                continue;
            }
            TextDecoder.DecodedSource decoded = decoder.decode(file.absolutePath());
            String text = decoded.text();
            List<Pattern> patterns = List.of(
                    Pattern.compile("<maven\\.compiler\\.(?:source|target)>\\s*([^<]+)"),
                    Pattern.compile("<java\\.version>\\s*([^<]+)"),
                    Pattern.compile("(?m)<source>\\s*([^<]+)"),
                    Pattern.compile("(?m)<target>\\s*([^<]+)"),
                    Pattern.compile("JavaVersion\\.VERSION_(\\d+)_(\\d+)"),
                    Pattern.compile("(?:sourceCompatibility|targetCompatibility)\\s*=\\s*['\"]?([\\d.]+)")
            );
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    if (matcher.groupCount() == 2) {
                        return matcher.group(1) + "." + matcher.group(2);
                    }
                    return matcher.group(1).trim();
                }
            }
        }
        return null;
    }

    public record ModuleSnapshot(
            String relativePath,
            String name,
            List<String> sourceRoots,
            List<String> testRoots,
            List<String> resourceRoots,
            List<String> webRoots,
            List<String> configRoots,
            List<String> sqlRoots
    ) {
        public String roots(PathRoleSelector selector) {
            return selector.select(this);
        }
    }

    public record ModuleLayout(
            List<ModuleSnapshot> modules,
            BuildTool buildTool,
            ProjectType projectType,
            String jdkVersion
    ) {
        public ModuleSnapshot moduleFor(String relativePath) {
            return modules.stream()
                    .filter(module -> module.relativePath().isBlank()
                            || relativePath.equals(module.relativePath())
                            || relativePath.startsWith(module.relativePath() + "/"))
                    .max(Comparator.comparingInt(module -> module.relativePath().length()))
                    .orElse(modules.getFirst());
        }
    }

    @FunctionalInterface
    public interface PathRoleSelector {
        String select(ModuleSnapshot module);
    }
}
