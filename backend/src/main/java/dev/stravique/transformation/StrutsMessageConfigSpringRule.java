package dev.stravique.transformation;

import dev.stravique.domain.FileType;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationArtifactType;
import dev.stravique.inventory.SecureXml;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

@Component
public class StrutsMessageConfigSpringRule implements TransformationRule {

    private static final String RULE_ID = "STRUTS-CONFIG-MESSAGE-SPRING";
    private static final String RULE_VERSION = "1.0.0";
    private static final String KEY = "spring.messages.basename";

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
        return "YAML";
    }

    @Override
    public String fileNameHint() {
        return "struts-config.xml";
    }

    @Override
    public boolean supports(MigrationItem item, ProjectFile file) {
        return item.getItemType() == MigrationItemType.CONFIGURATION
                && item.getTargetKey() == MigrationTarget.SPRING_BOOT_CONFIG
                && file.getFileType() == FileType.XML
                && file.getFileName().equalsIgnoreCase("struts-config.xml");
    }

    @Override
    public TransformationDraft propose(TransformationRequest request) {
        String parameter = messageResourcesParameter(request.source());
        List<TransformationCompanion> candidates = request.companions().stream()
                .filter(companion -> isSpringConfig(companion.relativePath()))
                .toList();
        if (candidates.size() != 1) {
            throw new TransformationUnsupportedException(
                    "Exactly one application.yml or application.properties target is required.");
        }
        TransformationCompanion target = candidates.get(0);
        if (target.source().contains(KEY)) {
            throw new TransformationUnsupportedException(
                    "The Spring message basename is already configured.");
        }
        String newline = target.source().contains("\r\n") ? "\r\n" : "\n";
        String prefix = target.source().isEmpty() || target.source().endsWith("\n")
                ? "" : newline;
        String line = isYaml(target.relativePath())
                ? KEY + ": \"" + escapeYaml(parameter) + "\""
                : KEY + "=" + parameter.replace("\\", "\\\\");
        String proposed = target.source() + prefix + line + newline;
        return new TransformationDraft(TransformationArtifactType.EDIT_FILE,
                target.relativePath(), proposed, target.source());
    }

    @Override
    public void validate(String proposed, String targetRelativePath) {
        if (isYaml(targetRelativePath)) {
            try {
                new Yaml().load(proposed);
            } catch (RuntimeException exception) {
                throw new TransformationInvalidException(
                        "The transformed Spring configuration is not valid YAML.");
            }
            return;
        }
        if (targetRelativePath.endsWith(".properties")) {
            try {
                new Properties().load(new StringReader(proposed));
            } catch (IOException | IllegalArgumentException exception) {
                throw new TransformationInvalidException(
                        "The transformed Spring configuration is not a valid properties file.");
            }
            return;
        }
        throw new TransformationInvalidException(
                "The Spring configuration target must be YAML or properties.");
    }

    private String messageResourcesParameter(String source) {
        try {
            var document = SecureXml.parse(source);
            var nodes = document.getElementsByTagName("message-resources");
            if (nodes.getLength() == 0) {
                throw new TransformationUnsupportedException(
                        "No Struts message-resources declaration was found.");
            }
            String parameter = ((org.w3c.dom.Element) nodes.item(0))
                    .getAttribute("parameter").trim();
            if (parameter.isBlank()) {
                throw new TransformationUnsupportedException(
                        "The Struts message-resources parameter is blank.");
            }
            if (parameter.chars().anyMatch(Character::isISOControl)) {
                throw new TransformationUnsupportedException(
                        "The Struts message-resources parameter contains control characters.");
            }
            return parameter;
        } catch (TransformationUnsupportedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TransformationInvalidException(
                    "The Struts configuration is not well-formed XML.");
        }
    }

    private boolean isSpringConfig(String relativePath) {
        String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        return name.equals("application.yml") || name.equals("application.yaml")
                || name.equals("application.properties");
    }

    private boolean isYaml(String relativePath) {
        return relativePath.endsWith(".yml") || relativePath.endsWith(".yaml");
    }

    private String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
