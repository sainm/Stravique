package dev.stravique.inventory;

import dev.stravique.api.ApiException;
import dev.stravique.config.StraviqueProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanPathPolicyTest {

    private final ScanPathPolicy policy = new ScanPathPolicy(new StraviqueProperties());

    @TempDir
    Path tempDir;

    @Test
    void resolvesAPlainDirectory() throws IOException {
        Path root = project();
        Path views = Files.createDirectories(root.resolve("web-app/views"));

        assertThat(policy.resolveProjectRelative(root, "web-app/views"))
                .isEqualTo(views.toRealPath());
    }

    @Test
    void refusesALinkThatEscapesTheProject() throws IOException {
        Path root = project();
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        symlink(root.resolve("escape"), outside);

        assertInvalidOverride(root, "escape");
    }

    @Test
    void refusesALinkHiddenInAParentSegment() throws IOException {
        Path root = project();
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.createDirectories(outside.resolve("child"));
        symlink(root.resolve("escape"), outside);

        // The lexical path "escape/child" still starts with the project root.
        assertInvalidOverride(root, "escape/child");
    }

    @Test
    void canonicalizesALinkThatStaysInsideTheProject() throws IOException {
        Path root = project();
        Path views = Files.createDirectories(root.resolve("web-app/views"));
        symlink(root.resolve("views-link"), views);

        assertThat(policy.resolveProjectRelative(root, "views-link"))
                .isEqualTo(views.toRealPath());
    }

    @Test
    void refusesParentTraversal() throws IOException {
        Path root = project();
        Files.createDirectories(tempDir.resolve("outside"));

        assertInvalidOverride(root, "../outside");
    }

    @Test
    void refusesAMissingDirectory() throws IOException {
        Path root = project();

        assertInvalidOverride(root, "not-there");
    }

    private Path project() throws IOException {
        // ProjectService passes the canonical root returned by resolveExistingProjectDirectory().
        return Files.createDirectories(tempDir.resolve("project")).toRealPath();
    }

    private void symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + exception);
        }
    }

    private void assertInvalidOverride(Path root, String relativePath) {
        assertThatThrownBy(() -> policy.resolveProjectRelative(root, relativePath))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> assertThat(((ApiException) thrown).getCode())
                        .isEqualTo("OVERRIDE_PATH_INVALID"));
    }
}
