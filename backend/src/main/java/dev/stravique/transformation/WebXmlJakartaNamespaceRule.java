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

import java.util.List;
import java.util.Set;

@Component
public class WebXmlJakartaNamespaceRule implements TransformationRule {

    private static final String RULE_ID = "WEBXML-JAKARTA-NAMESPACE";
    private static final String RULE_VERSION = "1.0.0";
    private static final String JAKARTA_NAMESPACE = "https://jakarta.ee/xml/ns/jakartaee";
    private static final String JAVAEE_NAMESPACE = "http://java.sun.com/xml/ns/javaee";
    private static final String J2EE_NAMESPACE = "http://java.sun.com/xml/ns/j2ee";
    private static final Set<String> OLD_VERSIONS = Set.of(
            "2.3", "2.4", "2.5", "3.0", "3.1", "4.0");
    private static final List<String> OLD_SCHEMAS = List.of(
            "web-app_2_3.xsd", "web-app_2_4.xsd", "web-app_2_5.xsd",
            "web-app_3_0.xsd", "web-app_3_1.xsd", "web-app_4_0.xsd");

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
        if (!OLD_VERSIONS.contains(version)) {
            throw new TransformationUnsupportedException(
                    "The web-app version is not a supported Java EE version.");
        }
        if (!source.contains(JAVAEE_NAMESPACE) && !source.contains(J2EE_NAMESPACE)) {
            throw new TransformationUnsupportedException(
                    "No Java EE web-app namespace was found.");
        }
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
}
