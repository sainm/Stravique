package dev.stravique.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JspTagScanner {

    private static final int MAX_TAG_LENGTH = 8192;
    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 512;

    private JspTagScanner() {
    }

    record Attribute(String name, String value, int line) {
    }

    record Directive(String name, Map<String, Attribute> attributes, int line) {
        String attribute(String key) {
            Attribute attribute = attributes.get(key.toLowerCase(Locale.ROOT));
            return attribute == null ? "" : attribute.value();
        }
    }

    record Tag(String prefix, String name, Map<String, Attribute> attributes, int line,
               boolean closing, boolean selfClosing) {

        String qualifiedName() {
            return prefix.isEmpty() ? name : prefix + ":" + name;
        }

        String attribute(String key) {
            Attribute attribute = attributes.get(key.toLowerCase(Locale.ROOT));
            return attribute == null ? "" : attribute.value();
        }

        boolean hasAttribute(String key) {
            return attributes.containsKey(key.toLowerCase(Locale.ROOT));
        }
    }

    record Document(List<Directive> directives, List<Tag> tags) {

        Map<String, Directive> taglibsByPrefix() {
            Map<String, Directive> result = new HashMap<>();
            for (Directive directive : directives) {
                if (!directive.name().equalsIgnoreCase("taglib")) {
                    continue;
                }
                String prefix = directive.attribute("prefix");
                if (!prefix.isBlank()) {
                    result.put(prefix, directive);
                }
            }
            return result;
        }
    }

    static Document scan(String text) {
        List<Directive> directives = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return new Document(List.copyOf(directives), List.copyOf(tags));
        }
        Cursor cursor = new Cursor(text);
        while (!cursor.done()) {
            if (cursor.current() != '<') {
                cursor.advance();
                continue;
            }
            if (cursor.startsWith("<!--")) {
                cursor.skipPast("-->");
            } else if (cursor.startsWith("<%--")) {
                cursor.skipPast("--%>");
            } else if (cursor.startsWith("<![CDATA[")) {
                cursor.skipPast("]]>");
            } else if (cursor.startsWith("<!")) {
                cursor.skipPast(">");
            } else if (cursor.startsWith("<?")) {
                cursor.skipPast("?>");
            } else if (cursor.startsWith("<%")) {
                int startLine = cursor.line();
                int contentStart = cursor.index() + 2;
                int end = text.indexOf("%>", contentStart);
                if (end < 0) {
                    break;
                }
                String content = text.substring(contentStart, end).stripLeading();
                if (content.startsWith("@")) {
                    Directive directive = parseDirective(content.substring(1), startLine);
                    if (directive != null) {
                        directives.add(directive);
                    }
                }
                cursor.advanceTo(end + 2);
            } else if (cursor.isTagStart()) {
                Tag tag = cursor.readTag();
                if (tag == null) {
                    cursor.advance();
                } else {
                    tags.add(tag);
                }
            } else {
                cursor.advance();
            }
        }
        return new Document(List.copyOf(directives), List.copyOf(tags));
    }

    private static Directive parseDirective(String content, int line) {
        int index = 0;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        int nameStart = index;
        while (index < content.length() && isNameCharacter(content.charAt(index))) {
            index++;
        }
        if (nameStart == index) {
            return null;
        }
        String name = content.substring(nameStart, index);
        Map<String, Attribute> attributes = parseAttributes(content, index, line);
        return new Directive(name, attributes, line);
    }

    private static Map<String, Attribute> parseAttributes(String text, int start, int line) {
        Map<String, Attribute> attributes = new HashMap<>();
        int index = start;
        while (index < text.length()) {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length()) {
                break;
            }
            int nameStart = index;
            while (index < text.length() && isAttributeNameCharacter(text.charAt(index))) {
                index++;
            }
            if (nameStart == index) {
                index++;
                continue;
            }
            String name = text.substring(nameStart, index);
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            String value = "";
            if (index < text.length() && text.charAt(index) == '=') {
                index++;
                while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
                if (index < text.length() && (text.charAt(index) == '"' || text.charAt(index) == '\'')) {
                    char quote = text.charAt(index);
                    index++;
                    int valueStart = index;
                    while (index < text.length() && text.charAt(index) != quote) {
                        index++;
                    }
                    value = text.substring(valueStart, Math.min(index, valueStart + MAX_ATTRIBUTE_VALUE_LENGTH));
                    if (index < text.length()) {
                        index++;
                    }
                } else {
                    int valueStart = index;
                    while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
                        index++;
                    }
                    value = text.substring(valueStart, Math.min(index, valueStart + MAX_ATTRIBUTE_VALUE_LENGTH));
                }
            }
            attributes.putIfAbsent(name.toLowerCase(Locale.ROOT), new Attribute(name, value, line));
        }
        return attributes;
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == ':' || character == '-'
                || character == '_' || character == '.';
    }

    private static boolean isAttributeNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || character == '-' || character == '_'
                || character == ':' || character == '.';
    }

    private static final class Cursor {

        private final String text;
        private int index;
        private int line = 1;

        private Cursor(String text) {
            this.text = text;
        }

        boolean done() {
            return index >= text.length();
        }

        char current() {
            return text.charAt(index);
        }

        int index() {
            return index;
        }

        int line() {
            return line;
        }

        void advance() {
            if (index < text.length()) {
                if (text.charAt(index) == '\n') {
                    line++;
                }
                index++;
            }
        }

        void advanceTo(int target) {
            while (index < target && index < text.length()) {
                advance();
            }
        }

        boolean startsWith(String value) {
            return text.startsWith(value, index);
        }

        void skipPast(String terminator) {
            int end = text.indexOf(terminator, index + 1);
            advanceTo(end < 0 ? text.length() : end + terminator.length());
        }

        boolean isTagStart() {
            char next = index + 1 < text.length() ? text.charAt(index + 1) : '\0';
            if (next == '/') {
                char after = index + 2 < text.length() ? text.charAt(index + 2) : '\0';
                return Character.isLetter(after);
            }
            return Character.isLetter(next);
        }

        Tag readTag() {
            int start = index;
            int startLine = line;
            advance();
            boolean closing = false;
            if (!done() && current() == '/') {
                closing = true;
                advance();
            }
            int nameStart = index;
            while (!done() && isNameCharacter(current())) {
                advance();
            }
            if (nameStart == index) {
                return null;
            }
            String qualified = text.substring(nameStart, index);
            String prefix = "";
            String name = qualified;
            int separator = qualified.indexOf(':');
            if (separator > 0) {
                prefix = qualified.substring(0, separator);
                name = qualified.substring(separator + 1);
            }
            if (name.isEmpty()) {
                return null;
            }
            if (!done() && !Character.isWhitespace(current()) && current() != '>'
                    && current() != '/') {
                index = start;
                line = startLine;
                return null;
            }
            Map<String, Attribute> attributes = new HashMap<>();
            boolean selfClosing = false;
            boolean terminated = false;
            int scanLimit = Math.min(text.length(), start + MAX_TAG_LENGTH);
            while (index < scanLimit) {
                char character = current();
                if (character == '>') {
                    selfClosing = index > start && text.charAt(index - 1) == '/';
                    advance();
                    terminated = true;
                    break;
                }
                if (Character.isWhitespace(character) || character == '/') {
                    advance();
                    continue;
                }
                int attributeStart = index;
                while (index < scanLimit && isAttributeNameCharacter(current())) {
                    advance();
                }
                if (attributeStart == index) {
                    advance();
                    continue;
                }
                String attributeName = text.substring(attributeStart, index);
                while (index < scanLimit && Character.isWhitespace(current())) {
                    advance();
                }
                String value = "";
                if (index < scanLimit && current() == '=') {
                    advance();
                    while (index < scanLimit && Character.isWhitespace(current())) {
                        advance();
                    }
                    if (index < scanLimit && (current() == '"' || current() == '\'')) {
                        char quote = current();
                        advance();
                        int valueStart = index;
                        while (index < scanLimit && current() != quote) {
                            advance();
                        }
                        value = text.substring(valueStart,
                                Math.min(index, valueStart + MAX_ATTRIBUTE_VALUE_LENGTH));
                        if (index < scanLimit) {
                            advance();
                        }
                    } else {
                        int valueStart = index;
                        while (index < scanLimit && !Character.isWhitespace(current())
                                && current() != '>') {
                            advance();
                        }
                        value = text.substring(valueStart,
                                Math.min(index, valueStart + MAX_ATTRIBUTE_VALUE_LENGTH));
                    }
                }
                attributes.putIfAbsent(attributeName.toLowerCase(Locale.ROOT),
                        new Attribute(attributeName, value, startLine));
            }
            if (!terminated) {
                index = start;
                line = startLine;
                return null;
            }
            return new Tag(prefix, name, attributes, startLine, closing, selfClosing);
        }
    }
}
