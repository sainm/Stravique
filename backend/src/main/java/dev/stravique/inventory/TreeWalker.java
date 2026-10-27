package dev.stravique.inventory;

import dev.stravique.config.StraviqueProperties;
import dev.stravique.domain.FileType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TreeWalker {

    private static final Set<String> DEFAULT_EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", ".idea", ".settings", ".gradle", "node_modules",
            "target", "build", "out", "dist", "coverage", ".next", ".cache"
    );

    private final FileClassifier classifier;
    private final StraviqueProperties properties;

    public TreeWalker(FileClassifier classifier, StraviqueProperties properties) {
        this.classifier = classifier;
        this.properties = properties;
    }

    public WalkResult walk(Path root, List<String> excludedDirectories, List<String> excludedFiles)
            throws IOException {
        List<DiscoveredFile> files = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> excludedDirectoryPatterns = normalizedExclusions(excludedDirectories);
        Set<String> excludedFilePatterns = normalizedExclusions(excludedFiles);
        Set<Path> realFiles = new HashSet<>();

        Files.walkFileTree(root, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (directory.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String relative = relative(root, directory);
                String name = directory.getFileName().toString();
                if (DEFAULT_EXCLUDED_DIRECTORIES.contains(name) || matchesDirectory(relative, excludedDirectoryPatterns)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isSymbolicLink()) {
                    diagnostics.add(new Diagnostic(relative(root, file), "SYMLINK_SKIPPED",
                            "Symbolic links are not followed during inventory."));
                    return FileVisitResult.CONTINUE;
                }
                if (!attributes.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String relative = relative(root, file);
                String fileName = file.getFileName().toString();
                if (matchesFile(relative, fileName, excludedFilePatterns)) {
                    return FileVisitResult.CONTINUE;
                }
                var type = classifier.classify(relative);
                if (type.isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    Path real = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
                    if (!realFiles.add(real)) {
                        diagnostics.add(new Diagnostic(relative, "DUPLICATE_FILE_SKIPPED",
                                "A duplicate canonical file path was skipped."));
                        return FileVisitResult.CONTINUE;
                    }
                } catch (IOException exception) {
                    diagnostics.add(new Diagnostic(relative, "FILE_ACCESS_FAILED",
                            "The file could not be resolved for inventory."));
                    return FileVisitResult.CONTINUE;
                }
                boolean tooLarge = attributes.size() > properties.getScan().getMaxFileBytes();
                boolean generatedPath = isGeneratedPath(relative);
                files.add(new DiscoveredFile(file, relative, type.get(), attributes.size(),
                        attributes.lastModifiedTime().toInstant(), tooLarge, generatedPath));
                if (files.size() > properties.getScan().getMaxFiles()) {
                    throw new ScanLimitException("The configured scan file limit was exceeded.");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                diagnostics.add(new Diagnostic(relative(root, file), "PATH_ACCESS_FAILED",
                        "A path could not be read during inventory."));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
                return FileVisitResult.CONTINUE;
            }
        });

        files.sort(Comparator.comparing(DiscoveredFile::relativePath));
        return new WalkResult(List.copyOf(files), List.copyOf(diagnostics));
    }

    private Set<String> normalizedExclusions(List<String> entries) {
        Set<String> result = new LinkedHashSet<>();
        if (entries == null) {
            return result;
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String normalized = entry.trim().replace('\\', '/');
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            result.add(normalized);
        }
        return result;
    }

    private boolean matchesDirectory(String relative, Set<String> exclusions) {
        for (String exclusion : exclusions) {
            if (globMatch(relative, exclusion) || relative.startsWith(exclusion + "/")
                    || relative.endsWith("/" + exclusion) || segmentsContain(relative, exclusion)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFile(String relative, String fileName, Set<String> exclusions) {
        for (String exclusion : exclusions) {
            if (globMatch(relative, exclusion) || globMatch(fileName, exclusion)
                    || relative.startsWith(exclusion + "/")) {
                return true;
            }
        }
        return false;
    }

    private boolean segmentsContain(String relative, String exclusion) {
        if (exclusion.contains("/") || exclusion.contains("*")) {
            return false;
        }
        for (String segment : relative.split("/")) {
            if (segment.equals(exclusion)) {
                return true;
            }
        }
        return false;
    }

    private boolean globMatch(String value, String pattern) {
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return value.equals(pattern);
        }
        StringBuilder regex = new StringBuilder("^");
        for (char character : pattern.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(character)));
            }
        }
        regex.append('$');
        return value.matches(regex.toString());
    }

    private boolean isGeneratedPath(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.contains("/generated/") || lower.startsWith("generated/")
                || lower.contains("/generated-sources/") || lower.contains("/generated-test-sources/");
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    public record DiscoveredFile(
            Path absolutePath,
            String relativePath,
            FileType fileType,
            long size,
            java.time.Instant lastModifiedAt,
            boolean tooLarge,
            boolean generatedPath
    ) {
    }

    public record Diagnostic(String relativePath, String code, String message) {
    }

    public record WalkResult(List<DiscoveredFile> files, List<Diagnostic> diagnostics) {
    }

    public static class ScanLimitException extends RuntimeException {
        public ScanLimitException(String message) {
            super(message);
        }
    }
}
