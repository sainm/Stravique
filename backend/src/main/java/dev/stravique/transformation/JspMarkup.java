package dev.stravique.transformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JspMarkup {

    record Taglib(String prefix, String uri, int start, int end) {
    }

    record Tag(String prefix, String name, Map<String, String> attributes,
               boolean closing, boolean selfClosing,
               int tagStart, int tagEnd, int nameStart, int nameEnd) {
    }

    record Document(List<Taglib> taglibs, List<Tag> tags) {
    }

    private JspMarkup() {
    }

    static Document parse(String text) {
        List<Taglib> taglibs = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) != '<') {
                index++;
                continue;
            }
            if (text.startsWith("<!--", index)) {
                index = skipPast(text, index + 4, "-->");
                continue;
            }
            if (text.startsWith("<%--", index)) {
                index = skipPast(text, index + 4, "--%>");
                continue;
            }
            if (text.startsWith("<![CDATA[", index)) {
                index = skipPast(text, index + 9, "]]>");
                continue;
            }
            if (text.startsWith("<!", index)) {
                index = skipPast(text, index + 2, ">");
                continue;
            }
            if (text.startsWith("<?", index)) {
                index = skipPast(text, index + 2, "?>");
                continue;
            }
            if (text.startsWith("<%", index)) {
                int end = text.indexOf("%>", index + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("Unterminated JSP directive or scriptlet.");
                }
                String content = text.substring(index + 2, end).stripLeading();
                if (content.startsWith("@")) {
                    Taglib taglib = taglib(content.substring(1), index, end + 2);
                    if (taglib != null) {
                        taglibs.add(taglib);
                    }
                }
                index = end + 2;
                continue;
            }
            if (!tagStart(text, index)) {
                index++;
                continue;
            }
            int tagEnd = tagEnd(text, index);
            if (tagEnd < 0) {
                throw new IllegalArgumentException("Unterminated JSP tag.");
            }
            Tag tag = tag(text, index, tagEnd);
            if (tag != null) {
                tags.add(tag);
            }
            index = tagEnd + 1;
        }
        return new Document(List.copyOf(taglibs), List.copyOf(tags));
    }

    static void validateStructure(Document document) {
        Deque<String> stack = new ArrayDeque<>();
        for (Tag tag : document.tags()) {
            if (tag.prefix().isEmpty()) {
                continue;
            }
            String qualified = tag.prefix() + ":" + tag.name();
            if (tag.closing()) {
                if (stack.isEmpty() || !stack.peek().equals(qualified)) {
                    throw new IllegalArgumentException("Unbalanced JSP tag: " + qualified);
                }
                stack.pop();
            } else if (!tag.selfClosing()) {
                stack.push(qualified);
            }
        }
        if (!stack.isEmpty()) {
            throw new IllegalArgumentException("Unclosed JSP tag: " + stack.peek());
        }
    }

    private static Tag tag(String text, int start, int end) {
        boolean closing = text.charAt(start + 1) == '/';
        int nameStart = start + (closing ? 2 : 1);
        while (nameStart < end && Character.isWhitespace(text.charAt(nameStart))) {
            nameStart++;
        }
        int nameEnd = nameStart;
        while (nameEnd < end && isNameCharacter(text.charAt(nameEnd))) {
            nameEnd++;
        }
        if (nameEnd == nameStart) {
            return null;
        }
        String qualified = text.substring(nameStart, nameEnd);
        int separator = qualified.indexOf(':');
        String prefix = separator < 0 ? "" : qualified.substring(0, separator);
        String name = separator < 0 ? qualified : qualified.substring(separator + 1);
        Map<String, String> attributes = new LinkedHashMap<>();
        parseAttributes(text, nameEnd, end, attributes);
        boolean selfClosing = !closing && end > start && text.charAt(end - 1) == '/';
        return new Tag(prefix, name, attributes, closing, selfClosing,
                start, end, nameStart, nameEnd);
    }

    private static Taglib taglib(String content, int start, int end) {
        String trimmed = content.stripLeading();
        int index = 0;
        while (index < trimmed.length() && isNameCharacter(trimmed.charAt(index))) {
            index++;
        }
        if (!trimmed.substring(0, index).equalsIgnoreCase("taglib")) {
            return null;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        parseAttributes(trimmed, index, trimmed.length(), attributes);
        String prefix = attributes.getOrDefault("prefix", "").trim();
        String uri = attributes.getOrDefault("uri", "").trim();
        if (prefix.isEmpty() || uri.isEmpty()) {
            return null;
        }
        return new Taglib(prefix, uri, start, end);
    }

    private static void parseAttributes(String text, int start, int end,
                                        Map<String, String> attributes) {
        int index = start;
        while (index < end) {
            while (index < end && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= end || text.charAt(index) == '/' || text.charAt(index) == '>') {
                break;
            }
            int nameStart = index;
            while (index < end && !Character.isWhitespace(text.charAt(index))
                    && text.charAt(index) != '=' && text.charAt(index) != '/'
                    && text.charAt(index) != '>') {
                index++;
            }
            String name = text.substring(nameStart, index);
            while (index < end && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            String value = "";
            if (index < end && text.charAt(index) == '=') {
                index++;
                while (index < end && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
                if (index < end && (text.charAt(index) == '\'' || text.charAt(index) == '"')) {
                    char quote = text.charAt(index++);
                    int valueStart = index;
                    while (index < end && text.charAt(index) != quote) {
                        index++;
                    }
                    value = text.substring(valueStart, index);
                    if (index < end) {
                        index++;
                    }
                } else {
                    int valueStart = index;
                    while (index < end && !Character.isWhitespace(text.charAt(index))
                            && text.charAt(index) != '/' && text.charAt(index) != '>') {
                        index++;
                    }
                    value = text.substring(valueStart, index);
                }
            }
            if (!name.isEmpty()) {
                attributes.putIfAbsent(name.toLowerCase(Locale.ROOT), value);
            }
        }
    }

    private static boolean tagStart(String text, int index) {
        int next = index + 1;
        if (next >= text.length()) {
            return false;
        }
        if (text.charAt(next) == '/') {
            next++;
        }
        return next < text.length() && Character.isLetter(text.charAt(next));
    }

    private static int tagEnd(String text, int start) {
        char quote = 0;
        for (int index = start + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
                continue;
            }
            if (character == '\'' || character == '"') {
                quote = character;
                continue;
            }
            if (character == '>') {
                return index;
            }
        }
        return -1;
    }

    private static int skipPast(String text, int from, String marker) {
        int index = text.indexOf(marker, from);
        return index < 0 ? text.length() : index + marker.length();
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character)
                || character == '_' || character == '-' || character == ':';
    }
}
