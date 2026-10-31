package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JspBeanMessageJstlRule implements TransformationRule {

    private static final String RULE_ID = "JSP-BEAN-MESSAGE-JSTL";
    private static final String RULE_VERSION = "1.2.0";
    private static final String LEGACY_JSTL_PREFIX = "http://java.sun.com/jsp/jstl/";
    private static final String JAKARTA_FMT_URI = "jakarta.tags.fmt";
    private static final String JAKARTA_CORE_URI = "jakarta.tags.core";
    private static final Map<String, String> JSTL_URIS = legacyJstlUris();

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String ruleVersion() {
        return RULE_VERSION;
    }

    @Override
    public MigrationItemType itemType() {
        return MigrationItemType.JSP_VIEW;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.KEEP_JSP;
    }

    @Override
    public String language() {
        return "JSP";
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.JSP_VIEW
                && item.getTargetKey() == MigrationTarget.KEEP_JSP
                && file.getFileType() == FileType.JSP;
    }

    @Override
    public String propose(String source, String relativePath) {
        JspMarkup.Document document = parse(source);
        List<JspMarkup.Taglib> legacyJstl = document.taglibs().stream()
                .filter(taglib -> taglib.uri().startsWith(LEGACY_JSTL_PREFIX))
                .toList();
        for (JspMarkup.Taglib taglib : legacyJstl) {
            if (!JSTL_URIS.containsKey(taglib.uri())) {
                throw new TransformationUnsupportedException(
                        "The legacy JSTL taglib " + taglib.uri() + " has no vetted replacement.");
            }
        }
        JspMarkup.Taglib bean = document.taglibs().stream()
                .filter(taglib -> taglib.uri().contains("struts.apache.org/tags-bean"))
                .findFirst()
                .orElse(null);
        boolean orphanBeanTag = document.tags().stream()
                .anyMatch(tag -> tag.prefix().equals("bean")
                        && (tag.name().equals("message") || tag.name().equals("write")));
        if (bean == null && orphanBeanTag) {
            throw new TransformationUnsupportedException(
                    "No Struts bean taglib is declared.");
        }
        List<JspMarkup.Tag> messages = bean == null ? List.of()
                : document.tags().stream()
                .filter(tag -> !tag.closing() && tag.prefix().equals(bean.prefix())
                        && tag.name().equals("message"))
                .toList();
        List<JspMarkup.Tag> writes = bean == null ? List.of()
                : document.tags().stream()
                .filter(tag -> !tag.closing() && tag.prefix().equals(bean.prefix())
                        && tag.name().equals("write"))
                .toList();
        if (messages.isEmpty() && writes.isEmpty() && legacyJstl.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "No supported Struts tags or legacy JSTL taglibs were found.");
        }
        for (JspMarkup.Tag message : messages) {
            if (!message.selfClosing()) {
                throw new TransformationUnsupportedException(
                        "Only self-closing bean:message tags are supported.");
            }
            for (String attribute : message.attributes().keySet()) {
                if (!attribute.equals("key") && !attribute.equals("bundle")) {
                    throw new TransformationUnsupportedException(
                            "bean:message attributes other than key and bundle are not supported.");
                }
            }
            if (message.attributes().getOrDefault("key", "").isBlank()) {
                throw new TransformationUnsupportedException(
                        "bean:message requires a literal key attribute.");
            }
        }
        boolean reuseFmt = hasFmtTaglib(document);
        String fmtPrefix = fmtPrefix(document);
        if (!messages.isEmpty() && !reuseFmt && prefixUsed(document, fmtPrefix)) {
            throw new TransformationUnsupportedException(
                    "The fmt prefix is already bound to another taglib.");
        }
        boolean reuseCore = hasCoreTaglib(document);
        String corePrefix = corePrefix(document);
        if (!writes.isEmpty() && !reuseCore && prefixUsed(document, corePrefix)) {
            throw new TransformationUnsupportedException(
                    "The c prefix is already bound to another taglib.");
        }
        List<Edit> edits = new ArrayList<>();
        for (JspMarkup.Taglib taglib : legacyJstl) {
            int start = source.indexOf(taglib.uri(), taglib.start());
            if (start < 0 || start + taglib.uri().length() > taglib.end()) {
                throw new TransformationUnsupportedException(
                        "The JSTL taglib URI could not be located in its directive.");
            }
            edits.add(new Edit(start, start + taglib.uri().length(),
                    JSTL_URIS.get(taglib.uri())));
        }
        for (JspMarkup.Tag message : messages) {
            edits.add(new Edit(message.nameStart(), message.nameEnd(),
                    fmtPrefix + ":message"));
        }
        for (JspMarkup.Tag write : writes) {
            edits.add(new Edit(write.tagStart(), write.tagEnd() + 1,
                    writeReplacement(write, corePrefix)));
        }
        boolean remainingBeanTags = bean != null && document.tags().stream()
                .anyMatch(tag -> tag.prefix().equals(bean.prefix())
                        && !tag.name().equals("message") && !tag.name().equals("write"));
        boolean beanPrefixInExpression = bean != null
                && source.contains("${" + bean.prefix() + ":");
        if (bean != null && !remainingBeanTags && !beanPrefixInExpression) {
            Edit removal = directiveRemoval(source, bean);
            if (removal != null) {
                edits.add(removal);
            }
        }
        String converted = apply(source, edits);
        List<String> directives = new ArrayList<>();
        if (!messages.isEmpty() && !reuseFmt) {
            directives.add(taglibDirective(fmtPrefix, JAKARTA_FMT_URI));
        }
        if (!writes.isEmpty() && !reuseCore) {
            directives.add(taglibDirective(corePrefix, JAKARTA_CORE_URI));
        }
        if (!directives.isEmpty()) {
            String newline = converted.contains("\r\n") ? "\r\n" : "\n";
            int insertAt = directiveInsertionPoint(converted);
            String addition = String.join(newline, directives) + newline;
            String insertion;
            if (insertAt == 0) {
                insertion = addition;
            } else if (insertAt >= converted.length() && !converted.endsWith("\n")) {
                insertion = newline + addition;
            } else {
                insertion = addition;
            }
            converted = converted.substring(0, insertAt) + insertion
                    + converted.substring(insertAt);
        }
        parse(converted);
        rejectLegacyJstl(converted);
        return converted;
    }

    @Override
    public void validate(String proposed) {
        parse(proposed);
        rejectLegacyJstl(proposed);
    }

    private String writeReplacement(JspMarkup.Tag write, String corePrefix) {
        if (!write.selfClosing()) {
            throw new TransformationUnsupportedException(
                    "Only self-closing bean:write tags are supported.");
        }
        for (String attribute : write.attributes().keySet()) {
            if (!attribute.equals("name") && !attribute.equals("property")) {
                throw new TransformationUnsupportedException(
                        "bean:write attributes other than name and property are not supported.");
            }
        }
        String name = write.attributes().getOrDefault("name", "");
        if (!isIdentifier(name)) {
            throw new TransformationUnsupportedException(
                    "bean:write requires a literal name attribute.");
        }
        String property = write.attributes().get("property");
        String value;
        if (property == null) {
            value = name;
        } else {
            if (!isPropertyPath(property)) {
                throw new TransformationUnsupportedException(
                        "bean:write requires a literal property path.");
            }
            value = name + "." + property;
        }
        return "<" + corePrefix + ":out value=\"${" + value + "}\"/>";
    }

    private boolean isIdentifier(String value) {
        return value.matches("[A-Za-z_$][A-Za-z0-9_$]*");
    }

    private boolean isPropertyPath(String value) {
        return value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }

    private boolean prefixUsed(JspMarkup.Document document, String prefix) {
        return document.taglibs().stream().anyMatch(taglib -> taglib.prefix().equals(prefix))
                || document.tags().stream().anyMatch(tag -> tag.prefix().equals(prefix));
    }

    private JspMarkup.Document parse(String text) {
        try {
            JspMarkup.Document document = JspMarkup.parse(text);
            JspMarkup.validateStructure(document);
            return document;
        } catch (IllegalArgumentException exception) {
            throw new TransformationInvalidException(
                    "The JSP tag structure is not balanced.");
        }
    }

    private void rejectLegacyJstl(String text) {
        JspMarkup.Document document = parse(text);
        if (document.taglibs().stream()
                .anyMatch(taglib -> taglib.uri().startsWith(LEGACY_JSTL_PREFIX))) {
            throw new TransformationInvalidException(
                    "A legacy JSTL taglib URI remains.");
        }
    }

    private String fmtPrefix(JspMarkup.Document document) {
        return document.taglibs().stream()
                .filter(taglib -> isFmtUri(taglib.uri()))
                .findFirst()
                .map(JspMarkup.Taglib::prefix)
                .orElse("fmt");
    }

    private String corePrefix(JspMarkup.Document document) {
        return document.taglibs().stream()
                .filter(taglib -> isCoreUri(taglib.uri()))
                .findFirst()
                .map(JspMarkup.Taglib::prefix)
                .orElse("c");
    }

    private boolean hasFmtTaglib(JspMarkup.Document document) {
        return document.taglibs().stream()
                .anyMatch(taglib -> isFmtUri(taglib.uri()));
    }

    private boolean hasCoreTaglib(JspMarkup.Document document) {
        return document.taglibs().stream()
                .anyMatch(taglib -> isCoreUri(taglib.uri()));
    }

    private Edit directiveRemoval(String source, JspMarkup.Taglib bean) {
        int lineStart = source.lastIndexOf('\n', bean.start() - 1) + 1;
        int lineEnd = source.indexOf('\n', bean.end());
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        String line = source.substring(lineStart, lineEnd).trim();
        String directive = source.substring(bean.start(), bean.end()).trim();
        if (!line.equals(directive)) {
            return null;
        }
        int end = lineEnd < source.length() ? lineEnd + 1 : lineEnd;
        return new Edit(lineStart, end, "");
    }

    private int directiveInsertionPoint(String source) {
        int page = source.indexOf("<%@ page");
        if (page < 0) {
            page = source.indexOf("<%@page");
        }
        if (page < 0) {
            return 0;
        }
        int end = source.indexOf("%>", page);
        if (end < 0) {
            return 0;
        }
        int newline = source.indexOf('\n', end);
        return newline < 0 ? source.length() : newline + 1;
    }

    private String taglibDirective(String prefix, String uri) {
        return "<%@ taglib prefix=\"" + prefix + "\" uri=\"" + uri + "\" %>";
    }

    private boolean isFmtUri(String uri) {
        return uri.equals(LEGACY_JSTL_PREFIX + "fmt") || uri.equals(JAKARTA_FMT_URI);
    }

    private boolean isCoreUri(String uri) {
        return uri.equals(LEGACY_JSTL_PREFIX + "core") || uri.equals(JAKARTA_CORE_URI);
    }

    private String apply(String source, List<Edit> edits) {
        List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder result = new StringBuilder(source);
        for (Edit edit : ordered) {
            result.replace(edit.start(), edit.end(), edit.replacement());
        }
        return result.toString();
    }

    private static Map<String, String> legacyJstlUris() {
        Map<String, String> uris = new LinkedHashMap<>();
        uris.put(LEGACY_JSTL_PREFIX + "core", JAKARTA_CORE_URI);
        uris.put(LEGACY_JSTL_PREFIX + "fmt", JAKARTA_FMT_URI);
        uris.put(LEGACY_JSTL_PREFIX + "functions", "jakarta.tags.functions");
        uris.put(LEGACY_JSTL_PREFIX + "sql", "jakarta.tags.sql");
        uris.put(LEGACY_JSTL_PREFIX + "xml", "jakarta.tags.xml");
        return Map.copyOf(uris);
    }

    private record Edit(int start, int end, String replacement) {
    }
}
