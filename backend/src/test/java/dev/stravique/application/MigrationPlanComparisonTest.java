package dev.stravique.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationPlanComparisonTest {

    @Test
    void countsAddedRemovedChangedAndUnchangedItems() {
        MigrationPlanComparison.ItemView removed = item("c", "Removed", "c.java", 1, "READY",
                "MEDIUM", "ASSISTED", true);
        MigrationPlanComparison.ItemView changedBase = item("a", "Changed", "a.java", 1,
                "READY", "MEDIUM", "ASSISTED", true);
        MigrationPlanComparison.ItemView unchanged = item("b", "Unchanged", "b.java", 1,
                "READY", "MEDIUM", "ASSISTED", true);
        MigrationPlanComparison.ItemView changedCurrent = item("a", "Changed", "a.java", 1,
                "READY", "MEDIUM", "ASSISTED", false);
        MigrationPlanComparison.ItemView added = item("d", "Added", "d.java", 1, "READY",
                "MEDIUM", "ASSISTED", true);

        MigrationPlanComparison.Result result = MigrationPlanComparison.compare(
                List.of(changedCurrent, unchanged, added),
                List.of(removed, changedBase, unchanged));

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.changed()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.rows())
                .extracting(MigrationPlanComparison.Row::status)
                .containsExactly("ADDED", "REMOVED", "CHANGED");
        MigrationPlanComparison.Row removedRow = result.rows().get(1);
        assertThat(removedRow.item().title()).isEqualTo("Removed");
        assertThat(removedRow.changes()).isEmpty();
        MigrationPlanComparison.Row changedRow = result.rows().get(2);
        assertThat(changedRow.item().included()).isFalse();
        assertThat(changedRow.changes())
                .extracting(MigrationPlanComparison.FieldChange::field)
                .containsExactly("INCLUDED");
        assertThat(changedRow.changes().get(0).before()).isEqualTo("true");
        assertThat(changedRow.changes().get(0).after()).isEqualTo("false");
    }

    @Test
    void reportsEveryComparedFieldInStableOrder() {
        MigrationPlanComparison.ItemView before = item("key", "Old title", null, 1, "READY",
                "MEDIUM", "ASSISTED", true);
        MigrationPlanComparison.ItemView after = new MigrationPlanComparison.ItemView(
                "key", "CONFIGURATION", "SPRING_BOOT_CONFIG", "New title",
                "web-app/Config.java", 2, "MANUAL", "HIGH", "REVIEW_REQUIRED", false);

        MigrationPlanComparison.Result result = MigrationPlanComparison.compare(
                List.of(after), List.of(before));

        assertThat(result.changed()).isEqualTo(1);
        MigrationPlanComparison.Row row = result.rows().get(0);
        assertThat(row.changes())
                .extracting(MigrationPlanComparison.FieldChange::field)
                .containsExactly("INCLUDED", "STATUS", "PHASE", "RISK", "AUTOMATION",
                        "TITLE", "PRIMARY_FILE");
        MigrationPlanComparison.FieldChange pathChange = row.changes().get(6);
        assertThat(pathChange.before()).isNull();
        assertThat(pathChange.after()).isEqualTo("web-app/Config.java");
    }

    @Test
    void identicalPlansProduceNoRows() {
        MigrationPlanComparison.ItemView item = item("key", "Title", "file.java", 1,
                "READY", "MEDIUM", "ASSISTED", true);

        MigrationPlanComparison.Result result = MigrationPlanComparison.compare(
                List.of(item), List.of(item));

        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.rows()).isEmpty();
    }

    private MigrationPlanComparison.ItemView item(String key, String title, String path,
                                                  int phase, String status, String risk,
                                                  String automation, boolean included) {
        return new MigrationPlanComparison.ItemView(key, "CONFIGURATION",
                "SPRING_BOOT_CONFIG", title, path, phase, automation, risk, status, included);
    }
}
