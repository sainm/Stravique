package dev.stravique.api.dto;

import java.util.List;
import java.util.UUID;

public record ReportDescriptorDto(
        UUID scanId,
        String type,
        long rowCount,
        List<String> formats
) {
}
