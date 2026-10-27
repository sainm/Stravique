package dev.stravique.inventory;

import dev.stravique.domain.FileType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class FileClassifier {

    public Optional<FileType> classify(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        String lower = fileName.toLowerCase(Locale.ROOT);
        String lowerPath = normalized.toLowerCase(Locale.ROOT);

        if (lower.equals("pom.xml")) {
            return Optional.of(FileType.MAVEN_POM);
        }
        if (lower.equals("build.gradle") || lower.equals("build.gradle.kts")
                || lower.equals("settings.gradle") || lower.equals("settings.gradle.kts")) {
            return Optional.of(FileType.GRADLE);
        }
        if (lower.endsWith(".dicon")) {
            return Optional.of(FileType.DICON);
        }
        if (lower.endsWith(".tld")) {
            return Optional.of(FileType.TLD);
        }
        if (lowerPath.contains("/src/test/") || lower.endsWith("test.java")
                || lower.endsWith("tests.java") || lower.endsWith("spec.java")) {
            return Optional.of(FileType.TEST);
        }
        if (lower.endsWith(".java")) {
            return Optional.of(FileType.JAVA);
        }
        if (lower.endsWith(".jsp") || lower.endsWith(".jspf") || lower.endsWith(".tag")) {
            return Optional.of(FileType.JSP);
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return Optional.of(FileType.HTML);
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) {
            return Optional.of(FileType.JAVASCRIPT);
        }
        if (lower.endsWith(".css")) {
            return Optional.of(FileType.CSS);
        }
        if (lower.endsWith(".properties")) {
            return Optional.of(FileType.PROPERTIES);
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return Optional.of(FileType.YAML);
        }
        if (lower.endsWith(".sql")) {
            return Optional.of(FileType.SQL);
        }
        if (lower.endsWith(".xml")) {
            return Optional.of(FileType.XML);
        }
        return Optional.empty();
    }

    public boolean isConfiguration(FileType fileType) {
        return fileType == FileType.XML || fileType == FileType.PROPERTIES
                || fileType == FileType.YAML || fileType == FileType.DICON
                || fileType == FileType.TLD || fileType == FileType.MAVEN_POM
                || fileType == FileType.GRADLE;
    }

    public boolean isStaticAsset(FileType fileType) {
        return fileType == FileType.JAVASCRIPT || fileType == FileType.CSS || fileType == FileType.HTML;
    }
}
