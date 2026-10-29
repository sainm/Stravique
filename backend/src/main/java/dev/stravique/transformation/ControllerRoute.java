package dev.stravique.transformation;

public record ControllerRoute(
        String actionPath,
        String actionType,
        String forwardPath,
        String forwardName
) {
}