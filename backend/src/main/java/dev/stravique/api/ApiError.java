package dev.stravique.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String code,
        String messageKey,
        String message,
        List<Object> args,
        String requestId,
        Instant timestamp
) {
}
