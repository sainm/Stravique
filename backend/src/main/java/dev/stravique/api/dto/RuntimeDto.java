package dev.stravique.api.dto;

import java.util.List;

public record RuntimeDto(
        List<String> allowedScanRoots,
        long maxFileBytes,
        int maxFiles,
        String analyzerVersion,
        String defaultLocale,
        boolean readOnlyInventory
) {
}
