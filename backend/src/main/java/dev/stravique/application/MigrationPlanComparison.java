package dev.stravique.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MigrationPlanComparison {

    private MigrationPlanComparison() {
    }

    public static Result compare(List<ItemView> current, List<ItemView> base) {
        Map<String, ItemView> currentByKey = byKey(current);
        Map<String, ItemView> baseByKey = byKey(base);
        List<Row> rows = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int changed = 0;
        int unchanged = 0;
        for (Map.Entry<String, ItemView> entry : currentByKey.entrySet()) {
            ItemView before = baseByKey.get(entry.getKey());
            if (before == null) {
                added++;
                rows.add(new Row("ADDED", entry.getValue(), List.of()));
                continue;
            }
            List<FieldChange> changes = changes(before, entry.getValue());
            if (changes.isEmpty()) {
                unchanged++;
            } else {
                changed++;
                rows.add(new Row("CHANGED", entry.getValue(), changes));
            }
        }
        for (Map.Entry<String, ItemView> entry : baseByKey.entrySet()) {
            if (!currentByKey.containsKey(entry.getKey())) {
                removed++;
                rows.add(new Row("REMOVED", entry.getValue(), List.of()));
            }
        }
        rows.sort(Comparator.comparingInt((Row row) -> statusOrder(row.status()))
                .thenComparing(row -> row.item().title())
                .thenComparing(row -> row.item().itemKey()));
        return new Result(added, removed, changed, unchanged, rows);
    }

    private static Map<String, ItemView> byKey(List<ItemView> items) {
        Map<String, ItemView> result = new LinkedHashMap<>();
        for (ItemView item : items) {
            result.put(item.itemKey(), item);
        }
        return result;
    }

    private static List<FieldChange> changes(ItemView before, ItemView after) {
        List<FieldChange> changes = new ArrayList<>();
        addChange(changes, "INCLUDED", Boolean.toString(before.included()),
                Boolean.toString(after.included()));
        addChange(changes, "STATUS", before.status(), after.status());
        addChange(changes, "PHASE", Integer.toString(before.phase()),
                Integer.toString(after.phase()));
        addChange(changes, "RISK", before.riskLevel(), after.riskLevel());
        addChange(changes, "AUTOMATION", before.automationLevel(), after.automationLevel());
        addChange(changes, "TITLE", before.title(), after.title());
        addChange(changes, "PRIMARY_FILE", before.primaryFilePath(), after.primaryFilePath());
        return changes;
    }

    private static void addChange(List<FieldChange> changes, String field, String before,
                                  String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, before, after));
        }
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "ADDED" -> 0;
            case "REMOVED" -> 1;
            default -> 2;
        };
    }

    public record ItemView(String itemKey, String itemType, String targetKey, String title,
                           String primaryFilePath, int phase, String automationLevel,
                           String riskLevel, String status, boolean included) {
    }

    public record FieldChange(String field, String before, String after) {
    }

    public record Row(String status, ItemView item, List<FieldChange> changes) {
    }

    public record Result(int added, int removed, int changed, int unchanged, List<Row> rows) {
    }
}
