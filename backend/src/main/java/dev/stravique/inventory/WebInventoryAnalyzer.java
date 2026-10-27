package dev.stravique.inventory;

import dev.stravique.domain.AnalysisFinding;
import dev.stravique.domain.AnalysisKind;
import dev.stravique.domain.Certainty;
import dev.stravique.domain.ParseStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebInventoryAnalyzer {

    private static final Pattern TAGLIB = Pattern.compile(
            "(?i)<%@\\s*taglib\\b([^%]*)%>");
    private static final Pattern URI = Pattern.compile("(?i)uri\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern PREFIX = Pattern.compile("(?i)prefix\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern FORM = Pattern.compile("(?i)<form\\b[^>]*action\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");
    private static final Pattern STATIC_REF = Pattern.compile(
            "(?i)<(?:script|link)\\b[^>]*(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>");
    private static final Pattern CUSTOM_TAG = Pattern.compile("<([a-zA-Z][\\w-]*):[a-zA-Z]");
    private static final Pattern FORM_FIELD = Pattern.compile(
            "(?i)<(?:input|select|textarea)\\b[^>]*\\bname\\s*=\\s*[\"']([^\"']+)[\"']");

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

        Set<String> customPrefixes = new LinkedHashSet<>();
        List<String> lines = context.text().lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;
            Matcher taglib = TAGLIB.matcher(line);
            if (taglib.find()) {
                Matcher uriMatcher = URI.matcher(taglib.group(1));
                Matcher prefixMatcher = PREFIX.matcher(taglib.group(1));
                String uri = uriMatcher.find() ? uriMatcher.group(1) : "";
                String prefix = prefixMatcher.find() ? prefixMatcher.group(1) : "";
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSP_TAGLIB",
                        "JSP tag library declaration" + (uri.isBlank() ? "" : " for " + uri) + ".",
                        "JSP-TAGLIB-001", lineNumber, Certainty.ANALYZED, ParseStatus.PARTIAL,
                        "{\"prefix\":\"" + json(prefix) + "\",\"uri\":\"" + json(uri) + "\"}"));
                String lower = uri.toLowerCase(Locale.ROOT);
                if (lower.contains("jstl") || lower.contains("jakarta.tags")) {
                    context.ledger().recordConfiguration("JSTL", "JSTL", context.relativePath(),
                            lineNumber, "JSP-JSTL-001");
                    output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSTL",
                            "JSTL tag library declaration detected.", "JSP-JSTL-001",
                            lineNumber, Certainty.ANALYZED, ParseStatus.PARTIAL, null));
                } else if (lower.contains("struts")) {
                    context.ledger().recordConfiguration("STRUTS1", "Struts 1",
                            context.relativePath(), lineNumber, "JSP-STRUTS-TAGLIB-001");
                    output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                            "Struts tag library declaration detected.",
                            "JSP-STRUTS-TAGLIB-001", lineNumber, Certainty.ANALYZED,
                            ParseStatus.PARTIAL, null));
                } else if (!uri.isBlank() && !prefix.isBlank()) {
                    customPrefixes.add(prefix);
                    context.ledger().recordConfiguration("CUSTOM_TAGS", "Custom JSP tags",
                            context.relativePath(), lineNumber, "JSP-CUSTOM-TAGLIB-001");
                    output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                            "Custom JSP tag library declaration detected.",
                            "JSP-CUSTOM-TAGLIB-001", lineNumber, Certainty.ANALYZED,
                            ParseStatus.PARTIAL, null));
                }
            }

            Matcher form = FORM.matcher(line);
            if (form.find()) {
                output.add(findings.create(context, AnalysisKind.REVIEW_ITEM, "FORM_ACTION",
                        "Form action " + form.group(1)
                                + " is a static candidate; runtime routing is not resolved.",
                        "JSP-FORM-ACTION-001", lineNumber, Certainty.REVIEW_REQUIRED,
                        ParseStatus.PARTIAL, "{\"action\":\"" + json(form.group(1)) + "\"}"));
            }

            Matcher field = FORM_FIELD.matcher(line);
            while (field.find()) {
                output.add(findings.create(context, AnalysisKind.CONFIGURATION,
                        "JSP_FORM_FIELD",
                        "Form field " + field.group(1) + " detected in page markup.",
                        "JSP-FORM-FIELD-001", lineNumber, Certainty.ANALYZED,
                        ParseStatus.PARTIAL,
                        "{\"field\":\"" + json(field.group(1)) + "\"}"));
            }

            Matcher staticRef = STATIC_REF.matcher(line);
            while (staticRef.find()) {
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STATIC_ASSET_REF",
                        "Static asset reference " + staticRef.group(1) + ".",
                        "JSP-STATIC-REF-001", lineNumber, Certainty.ANALYZED,
                        ParseStatus.PARTIAL, "{\"target\":\"" + json(staticRef.group(1)) + "\"}"));
            }

            if (line.toLowerCase(Locale.ROOT).contains("<fmt:message")) {
                context.ledger().recordConfiguration("JSTL", "JSTL", context.relativePath(),
                        lineNumber, "JSP-MESSAGE-DISPLAY-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "JSTL",
                        "Message resource display tag detected.", "JSP-MESSAGE-DISPLAY-001",
                        lineNumber, Certainty.ANALYZED, ParseStatus.PARTIAL, null));
            }
            if (line.toLowerCase(Locale.ROOT).contains("<html:errors")
                    || line.toLowerCase(Locale.ROOT).contains("<html:messages")) {
                context.ledger().recordConfiguration("STRUTS1", "Struts 1",
                        context.relativePath(), lineNumber, "JSP-STRUTS-MESSAGE-001");
                output.add(findings.create(context, AnalysisKind.CONFIGURATION, "STRUTS1",
                        "Struts message or error display tag detected.",
                        "JSP-STRUTS-MESSAGE-001", lineNumber, Certainty.ANALYZED,
                        ParseStatus.PARTIAL, null));
            }
        }

        Matcher customTag = CUSTOM_TAG.matcher(context.text());
        while (customTag.find()) {
            String prefix = customTag.group(1);
            if (!customPrefixes.contains(prefix) || Set.of("c", "fmt", "fn", "html", "bean", "logic")
                    .contains(prefix)) {
                continue;
            }
            int line = lineOf(context.text(), customTag.start());
            context.ledger().recordConfiguration("CUSTOM_TAGS", "Custom JSP tags",
                    context.relativePath(), line, "JSP-CUSTOM-TAG-USE-001");
            output.add(findings.create(context, AnalysisKind.CONFIGURATION, "CUSTOM_TAGS",
                    "Custom tag usage with prefix " + prefix + ".",
                    "JSP-CUSTOM-TAG-USE-001", line, Certainty.ANALYZED, ParseStatus.PARTIAL, null));
        }
        return new AnalysisResult(List.copyOf(output), ParseStatus.PARTIAL, null);
    }

    private int lineOf(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
