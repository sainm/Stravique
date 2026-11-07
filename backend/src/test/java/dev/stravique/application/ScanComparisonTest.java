package dev.stravique.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanComparisonTest {

    @Test
    void reportsAddedRemovedChangedUnchangedAndUnknownFiles() {
        List<ScanComparison.FileView> base = List.of(
                file("same.java", "hash-same", 10),
                file("changed.java", "hash-before", 20),
                file("removed.java", "hash-removed", 30),
                file("unknown.java", null, 40)
        );
        List<ScanComparison.FileView> current = List.of(
                file("same.java", "hash-same", 10),
                file("changed.java", "hash-after", 21),
                file("unknown.java", null, 40),
                file("added.java", "hash-added", 50)
        );

        ScanComparison.Result result = ScanComparison.compare(current, base);

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.unknown()).isEqualTo(1);
        assertThat(result.rows()).extracting(ScanComparison.Row::status)
                .containsExactly("ADDED", "REMOVED", "CHANGED", "UNKNOWN");
        assertThat(result.rows()).extracting(ScanComparison.Row::relativePath)
                .containsExactly("added.java", "removed.java", "changed.java", "unknown.java");
        assertThat(result.rows().get(0).before()).isNull();
        assertThat(result.rows().get(0).after().sha256()).isEqualTo("hash-added");
        assertThat(result.rows().get(1).after()).isNull();
        assertThat(result.rows().get(2).before().sha256()).isEqualTo("hash-before");
        assertThat(result.rows().get(2).after().sha256()).isEqualTo("hash-after");
    }

    @Test
    void treatsASizeDifferenceAsChangedWhenTheHashIsMissing() {
        ScanComparison.Result result = ScanComparison.compare(
                List.of(file("unreadable.bin", null, 12)),
                List.of(file("unreadable.bin", null, 10)));

        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.unknown()).isZero();
        assertThat(result.rows()).singleElement()
                .extracting(ScanComparison.Row::status).isEqualTo("CHANGED");
    }

    @Test
    void keepsUnchangedFilesOutOfTheRowList() {
        ScanComparison.Result result = ScanComparison.compare(
                List.of(file("one.java", "hash", 1), file("two.java", "hash", 2)),
                List.of(file("one.java", "hash", 1), file("two.java", "hash", 2)));

        assertThat(result.unchanged()).isEqualTo(2);
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void ordersRowsByStatusThenPath() {
        ScanComparison.Result result = ScanComparison.compare(
                List.of(file("b-added.java", "b", 1), file("a-added.java", "a", 1)),
                List.of(file("z-removed.java", "z", 1), file("y-removed.java", "y", 1)));

        assertThat(result.rows()).extracting(ScanComparison.Row::relativePath)
                .containsExactly("a-added.java", "b-added.java", "y-removed.java", "z-removed.java");
    }

    private ScanComparison.FileView file(String path, String sha256, long sizeBytes) {
        return new ScanComparison.FileView(path, "JAVA", sizeBytes, sha256, "UTF-8",
                "ANALYZED", "PARSED", null, false);
    }
}
