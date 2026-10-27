package dev.stravique.inventory;

import dev.stravique.domain.BuildTool;
import dev.stravique.domain.FileType;
import dev.stravique.domain.ModuleDependencyType;
import dev.stravique.domain.ProjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleDependencyDiscoveryTest {

    @TempDir
    Path root;

    @Test
    void findsEvidenceForGradleProjectDependencies() throws Exception {
        Path buildFile = root.resolve("web-app/build.gradle");
        Files.createDirectories(buildFile.getParent());
        Files.writeString(buildFile, """
                dependencies {
                    implementation project(':domain')
                }
                """);
        TextDecoder decoder = new TextDecoder();
        ModuleDependencyDiscovery discovery = new ModuleDependencyDiscovery(decoder);
        ModuleDiscovery.ModuleSnapshot rootModule = module(".", "legacy");
        ModuleDiscovery.ModuleSnapshot webApp = module("web-app", "web-app");
        ModuleDiscovery.ModuleSnapshot domain = module("domain", "domain");
        ModuleDiscovery.ModuleLayout layout = new ModuleDiscovery.ModuleLayout(
                List.of(rootModule, webApp, domain),
                BuildTool.GRADLE,
                ProjectType.GRADLE_MULTI_MODULE,
                "17"
        );
        TreeWalker.WalkResult walk = new TreeWalker.WalkResult(
                List.of(new TreeWalker.DiscoveredFile(
                        buildFile,
                        "web-app/build.gradle",
                        FileType.GRADLE,
                        Files.size(buildFile),
                        Instant.now(),
                        false,
                        false
                )),
                List.of()
        );

        List<ModuleDependencyDiscovery.DeclaredDependency> dependencies =
                discovery.discover(root, layout, walk);

        assertThat(dependencies).singleElement().satisfies(dependency -> {
            assertThat(dependency.fromModulePath()).isEqualTo("web-app");
            assertThat(dependency.toModulePath()).isEqualTo("domain");
            assertThat(dependency.type()).isEqualTo(ModuleDependencyType.GRADLE_PROJECT);
            assertThat(dependency.evidencePath()).isEqualTo("web-app/build.gradle");
            assertThat(dependency.lineStart()).isEqualTo(2);
        });
    }

    private ModuleDiscovery.ModuleSnapshot module(String path, String name) {
        return new ModuleDiscovery.ModuleSnapshot(path, name,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
