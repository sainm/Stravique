package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.inventory.SecureXml;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebXmlJakartaNamespaceRule implements TransformationRule {

    private static final String RULE_ID = "WEBXML-JAKARTA-NAMESPACE";
    private static final String RULE_VERSION = "1.2.0";
    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";
    private static final String JAVAEE_NAMESPACE = "http://java.sun.com/xml/ns/javaee";
    private static final String J2EE_NAMESPACE = "http://java.sun.com/xml/ns/j2ee";
    private static final Set<String> OLD_VERSIONS = Set.of(
            "2.3", "2.4", "2.5", "3.0", "3.1", "4.0");
    private static final List<String> OLD_SCHEMAS = List.of(
            "web-app_2_3.xsd", "web-app_2_4.xsd", "web-app_2_5.xsd",
            "web-app_3_0.xsd", "web-app_3_1.xsd", "web-app_4_0.xsd");
    private static final Pattern CLASS_ELEMENT = Pattern.compile(
            "(?s)(<((?:[\\w.-]+:)?(?:servlet-class|filter-class|listener-class|exception-type))"
                    + "(?:\\s[^>]*)?>)(.*?)(</\\2\\s*>)");
    private static final Pattern CLASS_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]*");

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
        return MigrationItemType.CONFIGURATION;
    }

    @Override
    public MigrationTarget target() {
        return MigrationTarget.SPRING_BOOT_CONFIG;
    }

    @Override
    public String language() {
        return "XML";
    }

    @Override
    public String fileNameHint() {
        return "web.xml";
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.CONFIGURATION
                && item.getTargetKey() == MigrationTarget.SPRING_BOOT_CONFIG
                && file.getFileType() == FileType.XML
                && file.getFileName().equalsIgnoreCase("web.xml");
    }

    @Override
    public String propose(String source, String relativePath) {
        Document document = parse(source);
        Element root = document.getDocumentElement();
        if (!"web-app".equals(localName(root))) {
            throw new TransformationUnsupportedException(
                    "Only a web-app descriptor is supported.");
        }
        String version = root.getAttribute("version").trim();
        String namespace = root.getNamespaceURI();
        boolean migrateNamespace;
        if (JAVAEE_NAMESPACE.equals(namespace) || J2EE_NAMESPACE.equals(namespace)) {
            if (!OLD_VERSIONS.contains(version)) {
                throw new TransformationUnsupportedException(
                        "The web-app version is not a supported Java EE version.");
            }
            migrateNamespace = true;
        } else if (JAKARTA_NAMESPACE.equals(namespace)) {
            if (!"6.0".equals(version)) {
                throw new TransformationUnsupportedException(
                        "The web-app version is not a supported Jakarta EE version.");
            }
            migrateNamespace = false;
        } else {
            throw new TransformationUnsupportedException(
                    "No Java EE or Jakarta web-app namespace was found.");
        }
        String proposed = rewriteClassNames(source);
        if (migrateNamespace) {
            proposed = migrateNamespace(proposed, version);
        } else if (proposed.equals(source)) {
            throw new TransformationUnsupportedException(
                    "The descriptor already uses the Jakarta namespace and no supported class name was found.");
        }
        validate(proposed);
        return proposed;
    }

    @Override
    public void validate(String proposed) {
        Document document = parse(proposed);
        Element root = document.getDocumentElement();
        if (!"web-app".equals(localName(root))
                || !JAKARTA_NAMESPACE.equals(root.getNamespaceURI())
                || !"6.0".equals(root.getAttribute("version").trim())) {
            throw new TransformationInvalidException(
                    "The descriptor is not a Jakarta EE 6.0 web-app.");
        }
        if (containsJavaxClass(proposed)) {
            throw new TransformationInvalidException(
                    "The descriptor still references a javax class name.");
        }
    }

    private String migrateNamespace(String source, String version) {
        String rootTag = rootTag(source);
        String oldVersionAttribute = "version=\"" + version + "\"";
        if (!rootTag.contains(oldVersionAttribute)) {
            throw new TransformationUnsupportedException(
                    "The web-app version attribute is not in double quotes.");
        }
        String proposed = source.replace(JAVAEE_NAMESPACE, JAKARTA_NAMESPACE)
                .replace(J2EE_NAMESPACE, JAKARTA_NAMESPACE);
        for (String schema : OLD_SCHEMAS) {
            proposed = proposed.replace(schema, "web-app_6_0.xsd");
        }
        int rootStart = proposed.indexOf("<web-app");
        int rootEnd = proposed.indexOf('>', rootStart);
        if (rootStart < 0 || rootEnd < 0) {
            throw new TransformationUnsupportedException(
                    "The web-app start tag could not be located.");
        }
        String updatedRoot = proposed.substring(rootStart, rootEnd + 1)
                .replace(oldVersionAttribute, "version=\"6.0\"");
        proposed = proposed.substring(0, rootStart) + updatedRoot
                + proposed.substring(rootEnd + 1);
        if (proposed.equals(source)) {
            throw new TransformationUnsupportedException(
                    "The descriptor already uses the Jakarta namespace.");
        }
        return proposed;
    }

    private String rewriteClassNames(String source) {
        Matcher matcher = CLASS_ELEMENT.matcher(maskUneditableSections(source));
        List<Edit> edits = new ArrayList<>();
        while (matcher.find()) {
            String content = source.substring(matcher.start(3), matcher.end(3));
            String trimmed = content.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!CLASS_NAME.matcher(trimmed).matches()) {
                throw new TransformationUnsupportedException(
                        "A web.xml class element does not contain a single class name.");
            }
            if (!trimmed.startsWith("javax.")) {
                continue;
            }
            if (!JakartaMappings.supports(trimmed)) {
                throw new TransformationUnsupportedException(
                        "The descriptor references a javax class without a vetted Jakarta mapping.");
            }
            int start = matcher.start(3) + content.indexOf(trimmed);
            edits.add(new Edit(start, start + trimmed.length(),
                    JakartaMappings.toJakarta(trimmed)));
        }
        List<Edit> sorted = edits.stream()
                .sorted(Comparator.comparingInt(Edit::start).reversed())
                .toList();
        String proposed = source;
        for (Edit edit : sorted) {
            proposed = proposed.substring(0, edit.start()) + edit.replacement()
                    + proposed.substring(edit.end());
        }
        return proposed;
    }

    private boolean containsJavaxClass(String source) {
        Matcher matcher = CLASS_ELEMENT.matcher(maskUneditableSections(source));
        while (matcher.find()) {
            String trimmed = source.substring(matcher.start(3), matcher.end(3)).trim();
            if (trimmed.startsWith("javax.")) {
                return true;
            }
        }
        return false;
    }

    private String maskUneditableSections(String source) {
        char[] characters = source.toCharArray();
        int index = 0;
        while (index < source.length()) {
            int comment = source.indexOf("<!--", index);
            int cdata = source.indexOf("<![CDATA[", index);
            if (comment < 0 && cdata < 0) {
                break;
            }
            String open;
            String close;
            int start;
            if (cdata < 0 || (comment >= 0 && comment < cdata)) {
                open = "<!--";
                close = "-->";
                start = comment;
            } else {
                open = "<![CDATA[";
                close = "]]>";
                start = cdata;
            }
            int end = source.indexOf(close, start + open.length());
            if (end < 0) {
                break;
            }
            for (int cursor = start; cursor < end + close.length(); cursor++) {
                if (characters[cursor] != '\n' && characters[cursor] != '\r') {
                    characters[cursor] = ' ';
                }
            }
            index = end + close.length();
        }
        return new String(characters);
    }

    private Document parse(String text) {
        try {
            return SecureXml.parse(text);
        } catch (Exception exception) {
            throw new TransformationInvalidException(
                    "The descriptor is not well-formed XML.");
        }
    }

    private String rootTag(String source) {
        int start = source.indexOf("<web-app");
        if (start < 0) {
            throw new TransformationUnsupportedException(
                    "The web-app start tag could not be located.");
        }
        int end = source.indexOf('>', start);
        if (end < 0) {
            throw new TransformationUnsupportedException(
                    "The web-app start tag could not be located.");
        }
        return source.substring(start, end + 1);
    }

    private String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private record Edit(int start, int end, String replacement) {
    }
}
