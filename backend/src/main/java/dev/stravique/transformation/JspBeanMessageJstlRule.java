package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class JspBeanMessageJstlRule implements TransformationRule {

    private static final String RULE_ID = "JSP-BEAN-MESSAGE-JSTL";
    private static final String RULE_VERSION = "1.0.0";
    private static final String JSTL_FMT_URI = "http://java.sun.com/jsp/jstl/fmt";
    private static final String JAKARTA_FMT_URI = "jakarta.tags.fmt";

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
        JspMarkup.Taglib bean = document.taglibs().stream()
                .filter(taglib -> taglib.uri().contains("struts.apache.org/tags-bean"))
                .findFirst()
                .orElse(null);
        if (bean == null) {
            throw new TransformationUnsupportedException(
                    "No Struts bean taglib is declared.");
        }
        List<JspMarkup.Tag> messages = document.tags().stream()
                .filter(tag -> !tag.closing() && tag.prefix().equals(bean.prefix())
                        && tag.name().equals("message"))
                .toList();
        if (messages.isEmpty()) {
            throw new TransformationUnsupportedException(
                    "No bean:message tags were found.");
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
        JspMarkup.Taglib fmt = document.taglibs().stream()
                .filter(taglib -> isFmtUri(taglib.uri()))
                .findFirst()
                .orElse(null);
        boolean reuseFmt = fmt != null;
        String fmtPrefix = reuseFmt ? fmt.prefix() : "fmt";
        if (!reuseFmt) {
            boolean fmtPrefixUsed = document.taglibs().stream()
                    .anyMatch(taglib -> taglib.prefix().equals(fmtPrefix))
                    || document.tags().stream()
                    .anyMatch(tag -> tag.prefix().equals(fmtPrefix));
            if (fmtPrefixUsed) {
                throw new TransformationUnsupportedException(
                        "The fmt prefix is already bound to another taglib.");
            }
        }
        List<Edit> edits = new ArrayList<>();
        for (JspMarkup.Tag message : messages) {
            edits.add(new Edit(message.nameStart(), message.nameEnd(),
                    fmtPrefix + ":message"));
        }
        boolean remainingBeanTags = document.tags().stream()
                .anyMatch(tag -> tag.prefix().equals(bean.prefix())
                        && !(tag.name().equals("message") && !tag.closing()));
        boolean beanPrefixInExpression = source.contains("${" + bean.prefix() + ":");
        if (!remainingBeanTags && !beanPrefixInExpression) {
            Edit removal = directiveRemoval(source, bean);
            if (removal != null) {
                edits.add(removal);
            }
        }
        String converted = apply(source, edits);
        if (!reuseFmt) {
            String directive = "<%@ taglib prefix=\"" + fmtPrefix + "\" uri=\""
                    + fmtUri(document) + "\" %>";
            String newline = converted.contains("\r\n") ? "\r\n" : "\n";
            int insertAt = directiveInsertionPoint(converted);
            String insertion;
            if (insertAt == 0) {
                insertion = directive + newline;
            } else if (insertAt >= converted.length() && !converted.endsWith("\n")) {
                insertion = newline + directive + newline;
            } else {
                insertion = directive + newline;
            }
            converted = converted.substring(0, insertAt) + insertion
                    + converted.substring(insertAt);
        }
        parse(converted);
        return converted;
    }

    @Override
    public void validate(String proposed) {
        parse(proposed);
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

    private String fmtUri(JspMarkup.Document document) {
        boolean legacyJstl = document.taglibs().stream()
                .anyMatch(taglib -> taglib.uri().startsWith("http://java.sun.com/jsp/jstl/"));
        return legacyJstl ? JSTL_FMT_URI : JAKARTA_FMT_URI;
    }

    private boolean isFmtUri(String uri) {
        return uri.equals(JSTL_FMT_URI) || uri.equals(JAKARTA_FMT_URI);
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

    private record Edit(int start, int end, String replacement) {
    }
}
