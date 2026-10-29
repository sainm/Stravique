package dev.stravique.transformation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;

/**
 * Replaces the bytes of a source file and changes nothing else about it.
 *
 * <p>{@link Files#createTempFile} creates a file that only its owner can read, so a
 * replacement built on it silently narrows a {@code 0644} or {@code 0755} source file
 * to {@code 0600}. That is invisible in an editor but breaks a build that runs as a
 * different user, and it drops the executable bit that a version control system
 * tracks. This writer therefore creates its temporary sibling with the platform
 * default permissions, adopts the permissions of the file it replaces, and flushes the
 * content to disk before the move so the rename cannot be ordered ahead of the data.
 *
 * <p>Permissions are a POSIX concept. Where the file system does not model them, the
 * default for a newly created file applies and the copy still completes.
 */
public final class SafeFileWriter {

    private static final int MAX_NAME_ATTEMPTS = 8;

    private SafeFileWriter() {
    }

    public static void write(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Target has no parent directory: " + target);
        }
        Path temporary = createSibling(parent, permissionsOf(target));
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Cleanup must never mask the outcome: after a successful move there is
                // nothing left to delete, and after a failed one the move error is the
                // useful signal. A leftover scratch file is recoverable; a swallowed
                // replacement failure would leave the caller believing nothing changed.
            }
        }
    }

    private static Path createSibling(Path parent, Set<PosixFilePermission> inherited)
            throws IOException {
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            Path candidate = parent.resolve(".stravique-transform-" + UUID.randomUUID() + ".tmp");
            try {
                Path created = Files.createFile(candidate);
                if (inherited != null) {
                    applyPermissions(created, inherited);
                }
                return created;
            } catch (FileAlreadyExistsException ignored) {
                // A fresh identifier collided with an existing entry; pick another name.
            }
        }
        throw new IOException("Unable to create a sibling temporary file in " + parent);
    }

    private static Set<PosixFilePermission> permissionsOf(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            return Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | UnsupportedOperationException exception) {
            return null;
        }
    }

    private static void applyPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // The file system does not model POSIX permissions, so the default stands.
        }
    }
}
