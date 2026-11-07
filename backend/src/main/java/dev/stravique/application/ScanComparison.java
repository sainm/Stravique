package dev.stravique.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScanComparison {

    private ScanComparison() {
    }

    public static Result compare(List<FileView> current, List<FileView> base) {
        Map<String, FileView> currentByPath = byPath(current);
        Map<String, FileView> baseByPath = byPath(base);
        List<Row> rows = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int changed = 0;
        int unchanged = 0;
        int unknown = 0;
        for (Map.Entry<String, FileView> entry : currentByPath.entrySet()) {
            FileView after = entry.getValue();
            FileView before = baseByPath.get(entry.getKey());
            if (before == null) {
                added++;
                rows.add(new Row("ADDED", null, after));
                continue;
            }
            String status = status(before, after);
            switch (status) {
                case "UNCHANGED" -> unchanged++;
                case "CHANGED" -> {
                    changed++;
                    rows.add(new Row(status, before, after));
                }
                default -> {
                    unknown++;
                    rows.add(new Row("UNKNOWN", before, after));
                }
            }
        }
        for (Map.Entry<String, FileView> entry : baseByPath.entrySet()) {
            if (!currentByPath.containsKey(entry.getKey())) {
                removed++;
                rows.add(new Row("REMOVED", entry.getValue(), null));
            }
        }
        rows.sort(Comparator.comparingInt((Row row) -> statusOrder(row.status()))
                .thenComparing(Row::relativePath));
        return new Result(added, removed, changed, unchanged, unknown, rows);
    }

    private static Map<String, FileView> byPath(List<FileView> files) {
        Map<String, FileView> result = new LinkedHashMap<>();
        for (FileView file : files) {
            result.putIfAbsent(file.relativePath(), file);
        }
        return result;
    }

    private static String status(FileView before, FileView after) {
        if (before.sha256() != null && after.sha256() != null) {
            return before.sha256().equals(after.sha256()) ? "UNCHANGED" : "CHANGED";
        }
        if (before.sizeBytes() != after.sizeBytes()) {
            return "CHANGED";
        }
        return "UNKNOWN";
    }

    private static int statusOrder(String status) {
        return switch (status) {
            case "ADDED" -> 0;
            case "REMOVED" -> 1;
            case "CHANGED" -> 2;
            default -> 3;
        };
    }

    public record FileView(String relativePath, String fileType, long sizeBytes, String sha256,
                           String encoding, String scanStatus, String parseStatus,
                           String structureRole, boolean generated) {
    }

    public record Row(String status, FileView before, FileView after) {
        public String relativePath() {
            return after != null ? after.relativePath() : before.relativePath();
        }
    }

    public record Result(int added, int removed, int changed, int unchanged, int unknown,
                         List<Row> rows) {
    }
}
