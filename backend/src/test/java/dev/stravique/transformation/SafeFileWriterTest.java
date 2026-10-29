package dev.stravique.transformation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SafeFileWriterTest {

    private static final Set<PosixFilePermission> OTHERS_READABLE = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);

    private static final Set<PosixFilePermission> EXECUTABLE = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE);

    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsTheReadablePermissionsOfTheFileItReplaces() throws Exception {
        Path target = temporaryDirectory.resolve("OrderService.java");
        Files.writeString(target, "class OrderService {}\n");
        Files.setPosixFilePermissions(target, OTHERS_READABLE);

        SafeFileWriter.write(target,
                "class OrderService { int pageSize; }\n".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(target)).contains("int pageSize");
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(OTHERS_READABLE);
    }

    @Test
    void keepsTheExecutableBitOfTheFileItReplaces() throws Exception {
        Path target = temporaryDirectory.resolve("build.sh");
        Files.writeString(target, "gradle build\n");
        Files.setPosixFilePermissions(target, EXECUTABLE);

        SafeFileWriter.write(target, "gradle clean build\n".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(EXECUTABLE);
    }

    @Test
    void givesANewFileThePlatformDefaultPermissions() throws Exception {
        Path probe = temporaryDirectory.resolve("probe.txt");
        Files.createFile(probe);
        Set<PosixFilePermission> platformDefault = Files.getPosixFilePermissions(probe);

        Path target = temporaryDirectory.resolve("OrderController.java");
        SafeFileWriter.write(target, "class OrderController {}\n".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(platformDefault);
    }

    @Test
    void replacesTheWholeFileRatherThanItsPrefix() throws Exception {
        Path target = temporaryDirectory.resolve("messages.properties");
        Files.writeString(target, "title=Orders\nsubtitle=Pending orders\n");

        SafeFileWriter.write(target, "title=Orders\n".getBytes(StandardCharsets.UTF_8));

        assertThat(Files.readString(target)).isEqualTo("title=Orders\n");
    }

    @Test
    void leavesNoTemporaryFileBehind() throws Exception {
        Path target = temporaryDirectory.resolve("OrderForm.java");
        Files.writeString(target, "class OrderForm {}\n");

        SafeFileWriter.write(target, "class OrderForm { String name; }\n"
                .getBytes(StandardCharsets.UTF_8));

        try (Stream<Path> entries = Files.list(temporaryDirectory)) {
            assertThat(entries.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".stravique-transform-"));
        }
    }
}
