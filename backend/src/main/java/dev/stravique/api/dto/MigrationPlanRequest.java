package dev.stravique.api.dto;

import java.util.UUID;

public record MigrationPlanRequest(
        UUID scanId,
        String targetProfile
) {
}
