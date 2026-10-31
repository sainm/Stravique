package dev.stravique.transformation;

import java.util.List;

final class JakartaMappings {

    private static final List<String> SUPPORTED_PREFIXES = List.of(
            "javax.servlet.",
            "javax.annotation.",
            "javax.persistence.",
            "javax.validation.",
            "javax.inject.",
            "javax.transaction.",
            "javax.xml.bind.",
            "javax.ws.rs.",
            "javax.mail.",
            "javax.activation."
    );

    private JakartaMappings() {
    }

    static boolean supports(String name) {
        return SUPPORTED_PREFIXES.stream()
                .anyMatch(prefix -> name.startsWith(prefix)
                        || (name + ".").equals(prefix));
    }

    static String toJakarta(String name) {
        return "jakarta." + name.substring("javax.".length());
    }
}
