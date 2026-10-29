package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Component
public class MessageResourceSpringUtf8Rule implements TransformationRule {

    private static final String RULE_ID = "MESSAGE-RESOURCE-SPRING-UTF8";
    private static final String RULE_VERSION = "1.0.0";

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
        return MigrationItemType.MESSAGE_RESOURCE;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_MESSAGES;
    }

    @Override
    public String language() {
        return "PROPERTIES";
    }

    @Override
    public String proposedEncoding() {
        return StandardCharsets.UTF_8.name();
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.MESSAGE_RESOURCE
                && item.getTargetKey() == MigrationTarget.SPRING_MESSAGES
                && file.getFileType() == FileType.PROPERTIES;
    }

    @Override
    public String propose(String source, String relativePath) {
        Properties original = load(source);
        String converted = convert(source);
        if (converted.equals(source)) {
            throw new TransformationUnsupportedException(
                    "No non-ASCII Unicode escapes were found in message values.");
        }
        if (!load(converted).equals(original)) {
            throw new TransformationInvalidException(
                    "The conversion would change message keys or values.");
        }
        return converted;
    }

    @Override
    public void validate(String proposed) {
        load(proposed);
        byte[] encoded = proposed.getBytes(StandardCharsets.UTF_8);
        if (!new String(encoded, StandardCharsets.UTF_8).equals(proposed)) {
            throw new TransformationInvalidException(
                    "The message resource cannot be represented as UTF-8.");
        }
    }

    private Properties load(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException exception) {
            throw new TransformationInvalidException(
                    "The message resource is not a valid properties file.");
        }
        return properties;
    }

    private String convert(String source) {
        StringBuilder output = new StringBuilder(source.length());
        boolean continuation = false;
        int index = 0;
        while (index < source.length()) {
            int lineEnd = index;
            while (lineEnd < source.length()
                    && source.charAt(lineEnd) != '\n' && source.charAt(lineEnd) != '\r') {
                lineEnd++;
            }
            String content = source.substring(index, lineEnd);
            String terminator = terminator(source, lineEnd);
            boolean endsWithContinuation = endsWithOddBackslashes(content);
            if (!continuation && !endsWithContinuation && !isComment(content)) {
                int valueStart = valueStart(content);
                if (valueStart >= 0) {
                    output.append(convertValue(content, valueStart));
                } else {
                    output.append(content);
                }
            } else {
                output.append(content);
            }
            output.append(terminator);
            continuation = endsWithContinuation;
            index = lineEnd + terminator.length();
        }
        return output.toString();
    }

    private String convertValue(String content, int start) {
        StringBuilder output = new StringBuilder(content.length());
        output.append(content, 0, start);
        int index = start;
        while (index < content.length()) {
            if (content.charAt(index) != '\\') {
                output.append(content.charAt(index++));
                continue;
            }
            int backslashes = 1;
            while (index + backslashes < content.length()
                    && content.charAt(index + backslashes) == '\\') {
                backslashes++;
            }
            int escape = index + backslashes;
            if (escape < content.length() && backslashes % 2 == 1
                    && content.charAt(escape) == 'u') {
                int code = decode(content, escape);
                if (code >= 0 && Character.isHighSurrogate((char) code)) {
                    int low = decode(content, escape + 6);
                    if (low >= 0 && Character.isLowSurrogate((char) low)) {
                        output.append("\\".repeat(backslashes - 1));
                        output.append((char) code).append((char) low);
                        index = escape + 11;
                        continue;
                    }
                } else if (code >= 0x00A0 && !Character.isSurrogate((char) code)) {
                    output.append("\\".repeat(backslashes - 1));
                    output.append((char) code);
                    index = escape + 5;
                    continue;
                }
            }
            output.append("\\".repeat(backslashes));
            index = escape;
        }
        return output.toString();
    }

    private int decode(String content, int escape) {
        if (escape + 4 >= content.length() || content.charAt(escape) != 'u') {
            return -1;
        }
        int value = 0;
        for (int offset = 1; offset <= 4; offset++) {
            int digit = Character.digit(content.charAt(escape + offset), 16);
            if (digit < 0) {
                return -1;
            }
            value = value * 16 + digit;
        }
        return value;
    }

    private int valueStart(String content) {
        int index = 0;
        while (index < content.length()) {
            char character = content.charAt(index);
            if (character == '\\') {
                index += 2;
                continue;
            }
            if (character == '=' || character == ':' || isSeparator(character)) {
                int start = index + 1;
                while (start < content.length() && isSeparator(content.charAt(start))) {
                    start++;
                }
                return start;
            }
            index++;
        }
        return -1;
    }

    private boolean isComment(String content) {
        int index = 0;
        while (index < content.length() && isSeparator(content.charAt(index))) {
            index++;
        }
        return index < content.length()
                && (content.charAt(index) == '#' || content.charAt(index) == '!');
    }

    private boolean endsWithOddBackslashes(String content) {
        int count = 0;
        for (int index = content.length() - 1; index >= 0
                && content.charAt(index) == '\\'; index--) {
            count++;
        }
        return count % 2 == 1;
    }

    private String terminator(String source, int lineEnd) {
        if (lineEnd >= source.length()) {
            return "";
        }
        if (source.charAt(lineEnd) == '\r' && lineEnd + 1 < source.length()
                && source.charAt(lineEnd + 1) == '\n') {
            return "\r\n";
        }
        return String.valueOf(source.charAt(lineEnd));
    }

    private boolean isSeparator(char character) {
        return character == ' ' || character == '\t' || character == '\f';
    }
}
