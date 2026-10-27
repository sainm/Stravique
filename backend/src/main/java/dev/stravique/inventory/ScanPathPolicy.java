package dev.stravique.inventory;

import dev.stravique.api.ApiException;
import dev.stravique.config.StraviqueProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ScanPathPolicy {

    private final StraviqueProperties properties;

    public ScanPathPolicy(StraviqueProperties properties) {
        this.properties = properties;
    }

    public Path validateProjectDirectory(String suppliedPath) {
        if (suppliedPath == null || suppliedPath.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_PATH_INVALID",
                    "error.project.sourceInvalid");
        }
        Path candidate = Path.of(suppliedPath).toAbsolutePath().normalize();
        try {
            if (!Files.exists(candidate) || !Files.isDirectory(candidate)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_PATH_INVALID",
                        "error.project.sourceInvalid");
            }
            Path real = candidate.toRealPath();
            Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            Path filesystemRoot = real.getRoot();
            if (real.equals(filesystemRoot) || real.equals(home)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_PATH_SENSITIVE",
                        "error.project.sensitiveRoot");
            }
            if (!isWithinAnyRoot(real)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_PATH_FORBIDDEN",
                        "error.project.sourceForbidden");
            }
            return real;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_PATH_INVALID",
                    "error.project.sourceInvalid");
        }
    }

    public Path resolveExistingProjectDirectory(String sourcePath) {
        try {
            return validateProjectDirectory(sourcePath);
        } catch (ApiException exception) {
            if ("SOURCE_PATH_INVALID".equals(exception.getCode())) {
                throw new ApiException(HttpStatus.CONFLICT, "SCAN_SOURCE_UNAVAILABLE",
                        "error.scan.sourceUnavailable");
            }
            throw exception;
        }
    }

    public Path resolveProjectRelative(Path projectRoot, String relativePath) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        Path resolved = projectRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OVERRIDE_PATH_INVALID",
                    "error.override.invalidPath");
        }
        return resolved;
    }

    public List<String> allowedRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        for (String configured : properties.getScan().getAllowedRoots()) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            for (String entry : configured.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    addIfDirectory(roots, Path.of(trimmed));
                }
            }
        }
        if (roots.isEmpty()) {
            Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            addIfDirectory(roots, workingDirectory);
            addIfDirectory(roots, workingDirectory.resolve("sample-projects"));
            if (workingDirectory.getParent() != null) {
                addIfDirectory(roots, workingDirectory.getParent());
                addIfDirectory(roots, workingDirectory.getParent().resolve("sample-projects"));
            }
        }
        List<String> values = new ArrayList<>();
        for (Path root : roots) {
            values.add(root.toString());
        }
        return List.copyOf(values);
    }

    private boolean isWithinAnyRoot(Path real) throws IOException {
        for (Path configured : configuredRootPaths()) {
            if (real.startsWith(configured)) {
                return true;
            }
        }
        return false;
    }

    private List<Path> configuredRootPaths() throws IOException {
        List<Path> roots = new ArrayList<>();
        for (String value : allowedRoots()) {
            Path root = Path.of(value).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                roots.add(root.toRealPath());
            }
        }
        return roots;
    }

    private void addIfDirectory(Set<Path> roots, Path candidate) {
        try {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
                roots.add(absolute.toRealPath());
            }
        } catch (IOException | SecurityException ignored) {
            // Invalid configured roots are intentionally omitted from the effective policy.
        }
    }
}
