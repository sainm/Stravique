package dev.stravique.transformation;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.github.javaparser.StaticJavaParser;
import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.ProjectFile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransformationEngine {

    private final List<TransformationRule> rules;

    public TransformationEngine(List<TransformationRule> rules) {
        this.rules = rules;
    }

    public TransformationOutcome propose(MigrationItem item, ProjectFile file, String source,
                                         String relativePath) {
        TransformationRule rule = rules.stream()
                .filter(candidate -> candidate.supports(item, file))
                .findFirst()
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "No vetted transformation rule supports this item."));
        String proposed;
        try {
            proposed = rule.propose(source, relativePath);
        } catch (TransformationUnsupportedException | TransformationInvalidException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TransformationInvalidException(
                    "The original source could not be transformed by this rule.");
        }
        if (proposed == null || proposed.equals(source)) {
            throw new TransformationUnsupportedException(
                    "The rule found no eligible construct to transform.");
        }
        try {
            StaticJavaParser.parse(proposed);
        } catch (Exception exception) {
            throw new TransformationInvalidException(
                    "The transformed source is not syntactically valid.");
        }
        String diff = diff(source, proposed, relativePath);
        return new TransformationOutcome(rule.ruleId(), rule.ruleVersion(), proposed, diff,
                "PARSED", null);
    }

    private String diff(String original, String proposed, String relativePath) {
        List<String> originalLines = List.of(original.split("\\R", -1));
        List<String> proposedLines = List.of(proposed.split("\\R", -1));
        Patch<String> patch = DiffUtils.diff(originalLines, proposedLines);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
                "a/" + relativePath, "b/" + relativePath, originalLines, patch, 3);
        return String.join("\n", unified);
    }
}
