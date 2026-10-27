package dev.stravique.inventory;

import dev.stravique.domain.FileType;
import dev.stravique.domain.PathOverride;
import dev.stravique.domain.PathRole;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class FileRoleResolver {

    public String resolve(String relativePath, FileType fileType, ModuleDiscovery.ModuleSnapshot module,
                          List<PathOverride> overrides) {
        String overrideRole = overrides.stream()
                .filter(override -> startsWithPath(relativePath, override.getRelativePath()))
                .max(Comparator.comparingInt(override -> override.getRelativePath().length()))
                .map(override -> override.getRole().name())
                .orElse(null);
        if (overrideRole != null) {
            return overrideRole;
        }
        if (underAny(relativePath, module.testRoots())) {
            return PathRole.TEST_SOURCE.name();
        }
        if (underAny(relativePath, module.sourceRoots())) {
            return PathRole.JAVA_SOURCE.name();
        }
        if (underAny(relativePath, module.webRoots())) {
            if (fileType == FileType.JSP) {
                return PathRole.JSP_ROOT.name();
            }
            if (fileType == FileType.JAVASCRIPT || fileType == FileType.CSS
                    || fileType == FileType.HTML) {
                return PathRole.STATIC_RESOURCE.name();
            }
            return PathRole.WEB_ROOT.name();
        }
        if (fileType == FileType.SQL || underAny(relativePath, module.sqlRoots())) {
            return PathRole.SQL.name();
        }
        if (underAny(relativePath, module.configRoots()) || isConfiguration(fileType)) {
            return PathRole.CONFIGURATION.name();
        }
        if (underAny(relativePath, module.resourceRoots())) {
            return PathRole.RESOURCE.name();
        }
        return null;
    }

    private boolean isConfiguration(FileType type) {
        return type == FileType.XML || type == FileType.DICON || type == FileType.PROPERTIES
                || type == FileType.YAML || type == FileType.TLD || type == FileType.MAVEN_POM
                || type == FileType.GRADLE;
    }

    private boolean underAny(String path, List<String> roots) {
        return roots.stream().anyMatch(root -> startsWithPath(path, root));
    }

    private boolean startsWithPath(String path, String root) {
        if (root == null || root.isBlank() || root.equals(".")) {
            return true;
        }
        String normalized = root.replace('\\', '/');
        return path.equals(normalized) || path.startsWith(normalized + "/");
    }
}
