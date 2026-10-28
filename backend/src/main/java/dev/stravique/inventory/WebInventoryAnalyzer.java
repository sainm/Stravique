package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class WebInventoryAnalyzer {

    private static final Set<String> STRUTS_FIELD_TAGS = Set.of(
            "text", "textarea", "select", "checkbox", "radio", "hidden", "password", "file", "multibox"
    );
    private static final Set<String> PLAIN_FIELD_TAGS = Set.of("input", "select", "textarea");

    private final FindingFactory findings;

    public WebInventoryAnalyzer(FindingFactory findings) {
        this.findings = findings;
    }

    public AnalysisResult analyzeJsp(AnalyzerContext context) {
        List<AnalysisFinding> output = new ArrayList<>();
        context.ledger().recordApi("JSP", "JSP", context.relativePath(), 1, "JSP-PAGE-001");
        output.add(findings.create(context, AnalysisKind.TECHNOLOGY, "JSP",
                "JSP page identified.", "JSP-PAGE-001", 1, Certainty.ANALYZED,
                ParseStatus.PARTIAL, null));

        JspTagScanner.Document document = JspTagScanner.scan(context.text());
        Map<String, Library> libraries = analyzeTaglibDirectives(context, document, output);
        for (JspTagScanner.Tag tag : document.tags()) {
            if (tag.closing()) {
                continue;
            }
            Library library = libraries.getOrDefault(tag.prefix(), Library.NONE);
            analyzeForm(context, tag, library, output);
            analyzeField(context, tag, library, output);
            analyzeMessage(context, tag, library, output);
            analyzeStaticReference(context, tag, output);
            if (library == Library.CUSTOM) {
                context.ledger().recordConfiguration("CUSTOM_TAGS", "Custom JSP tags",
                        context.relativePath(), tag.line(), "JSP-CUSTOM-TAG-USE-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                        "Custom tag " + tag.qualifiedName() + " used in page markup.",
                        "JSP-CUSTOM-TAG-USE-001", tag.line(), Certainty.ANALYZED,
                        ParseStatus.PARTIAL, "{\"tag\":\"" + json(tag.qualifiedName()) + "\"}"));
            }
        }
        return new AnalysisResult(List.copyOf(output), ParseStatus.PARTIAL, null);
    }

    private Map<String, Library> analyzeTaglibDirectives(AnalyzerContext context,
                                                         JspTagScanner.Document document,
                                                         List<AnalysisFinding> output) {
        Map<String, Library> libraries = new LinkedHashMap<>();
        for (JspTagScanner.Directive directive : document.directives()) {
            if (!directive.name().equalsIgnoreCase("taglib")) {
                continue;
            }
            String prefix = directive.attribute("prefix");
            String uri = directive.attribute("uri");
            if (prefix.isBlank()) {
                continue;
            }
            Library library = libraryFor(uri);
            libraries.put(prefix, library);
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSP_TAGLIB",
                    "JSP tag library declaration" + (uri.isBlank() ? "" : " for " + uri) + ".",
                    "JSP-TAGLIB-001", directive.line(), Certainty.ANALYZED, ParseStatus.PARTIAL,
                    "{\"prefix\":\"" + json(prefix) + "\",\"uri\":\"" + json(uri) + "\"}"));
            if (library == Library.JSTL) {
                context.ledger().recordConfiguration("JSTL", "JSTL", context.relativePath(),
                        directive.line(), "JSP-JSTL-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSTL",
                        "JSTL tag library declaration detected.", "JSP-JSTL-001",
                        directive.line(), Certainty.ANALYZED, ParseStatus.PARTIAL, null));
            } else if (library == Library.STRUTS) {
                context.ledger().recordConfiguration("STRUTS1", "Struts 1",
                        context.relativePath(), directive.line(), "JSP-STRUTS-TAGLIB-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                        "Struts tag library declaration detected.",
                        "JSP-STRUTS-TAGLIB-001", directive.line(), Certainty.ANALYZED,
                        ParseStatus.PARTIAL, null));
            } else if (library == Library.CUSTOM) {
                context.ledger().recordConfiguration("CUSTOM_TAGS", "Custom JSP tags",
                        context.relativePath(), directive.line(), "JSP-CUSTOM-TAGLIB-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                        "Custom JSP tag library declaration detected.",
                        "JSP-CUSTOM-TAGLIB-001", directive.line(), Certainty.ANALYZED,
                        ParseStatus.PARTIAL, null));
            }
        }
        return libraries;
    }

    private void analyzeForm(AnalyzerContext context, JspTagScanner.Tag tag, Library library,
                             List<AnalysisFinding> output) {
        if (!tag.name().equalsIgnoreCase("form")) {
            return;
        }
        boolean plain = tag.prefix().isEmpty();
        if (!plain && library != Library.STRUTS) {
            return;
        }
        String action = tag.attribute("action").strip();
        if (action.isEmpty()) {
            return;
        }
        output.add(findings.create(context, AnalysisKind.REVIEW_ITEM, "FORM_ACTION",
                "Form action " + action + " is a static candidate; runtime routing is not resolved.",
                "JSP-FORM-ACTION-001", tag.line(), Certainty.REVIEW_REQUIRED, ParseStatus.PARTIAL,
                "{\"action\":\"" + json(action) + "\",\"tag\":\"" + json(tag.qualifiedName()) + "\"}"));
    }

    private void analyzeField(AnalyzerContext context, JspTagScanner.Tag tag, Library library,
                              List<AnalysisFinding> output) {
        String field = "";
        boolean plain = tag.prefix().isEmpty() && PLAIN_FIELD_TAGS.contains(tag.name().toLowerCase(Locale.ROOT));
        if (plain) {
            field = tag.attribute("name").strip();
        } else if (library == Library.STRUTS
                && STRUTS_FIELD_TAGS.contains(tag.name().toLowerCase(Locale.ROOT))) {
            field = tag.attribute("property").strip();
        }
        if (field.isEmpty()) {
            return;
        }
        output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSP_FORM_FIELD",
                "Form field " + field + " detected in page markup.",
                "JSP-FORM-FIELD-001", tag.line(), Certainty.ANALYZED, ParseStatus.PARTIAL,
                "{\"field\":\"" + json(field) + "\",\"element\":\""
                        + json(tag.qualifiedName()) + "\"}"));
    }

    private void analyzeMessage(AnalyzerContext context, JspTagScanner.Tag tag, Library library,
                                List<AnalysisFinding> output) {
        String name = tag.name().toLowerCase(Locale.ROOT);
        if (name.equals("message") && (library == Library.JSTL || library == Library.STRUTS)) {
            String key = tag.attribute("key").strip();
            if (key.isEmpty()) {
                return;
            }
            if (library == Library.JSTL) {
                context.ledger().recordConfiguration("JSTL", "JSTL", context.relativePath(),
                        tag.line(), "JSP-MESSAGE-DISPLAY-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSTL",
                        "Message resource display tag for key " + key + " detected.",
                        "JSP-MESSAGE-DISPLAY-001", tag.line(), Certainty.ANALYZED,
                        ParseStatus.PARTIAL, "{\"key\":\"" + json(key) + "\"}"));
            } else {
                context.ledger().recordConfiguration("STRUTS1", "Struts 1",
                        context.relativePath(), tag.line(), "JSP-STRUTS-MESSAGE-DISPLAY-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                        "Struts message display tag for key " + key + " detected.",
                        "JSP-STRUTS-MESSAGE-DISPLAY-001", tag.line(), Certainty.ANALYZED,
                        ParseStatus.PARTIAL, "{\"key\":\"" + json(key) + "\"}"));
            }
            return;
        }
        if (library == Library.STRUTS && (name.equals("errors") || name.equals("messages"))) {
            context.ledger().recordConfiguration("STRUTS1", "Struts 1", context.relativePath(),
                    tag.line(), "JSP-STRUTS-MESSAGE-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                    "Struts message or error display tag detected.",
                    "JSP-STRUTS-MESSAGE-001", tag.line(), Certainty.ANALYZED,
                    ParseStatus.PARTIAL, "{\"tag\":\"" + json(tag.qualifiedName()) + "\"}"));
        }
    }

    private void analyzeStaticReference(AnalyzerContext context, JspTagScanner.Tag tag,
                                        List<AnalysisFinding> output) {
        String name = tag.name().toLowerCase(Locale.ROOT);
        String target = switch (name) {
            case "script", "img", "source" -> tag.attribute("src").strip();
            case "link" -> tag.attribute("href").strip();
            default -> "";
        };
        if (target.isEmpty()) {
            return;
        }
        output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STATIC_ASSET_REF",
                "Static asset reference " + target + ".",
                "JSP-STATIC-REF-001", tag.line(), Certainty.ANALYZED, ParseStatus.PARTIAL,
                "{\"target\":\"" + json(target) + "\",\"element\":\""
                        + json(tag.qualifiedName()) + "\"}"));
    }

    private Library libraryFor(String uri) {
        String lower = uri == null ? "" : uri.strip().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return Library.UNKNOWN;
        }
        if (lower.contains("jstl") || lower.contains("jakarta.tags")) {
            return Library.JSTL;
        }
        if (lower.startsWith("http://struts.apache.org") || lower.startsWith("https://struts.apache.org")
                || lower.startsWith("http://jakarta.apache.org/struts")
                || lower.startsWith("https://jakarta.apache.org/struts")) {
            return Library.STRUTS;
        }
        if (lower.contains("java.sun.com/jsp")) {
            return Library.STANDARD;
        }
        return Library.CUSTOM;
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum Library {
        NONE, UNKNOWN, STANDARD, JSTL, STRUTS, CUSTOM
    }
}
