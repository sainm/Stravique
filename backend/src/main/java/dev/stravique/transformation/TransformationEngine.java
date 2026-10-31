package dev.stravique.transformation;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransformationEngine {

    private final List<TransformationRule> rules;

    public TransformationEngine(List<TransformationRule> rules) {
        this.rules = rules;
    }

    public List<RuleDescriptor> rules() {
        return rules.stream()
                .map(rule -> new RuleDescriptor(rule.ruleId(), rule.ruleVersion(),
                        rule.itemType().name(), rule.target().name(), rule.language(),
                        rule.fileNameHint()))
                .sorted(java.util.Comparator.comparing(RuleDescriptor::ruleId))
                .toList();
    }

    public TransformationOutcome propose(TransformationRequest request) {
        TransformationRule rule = rules.stream()
                .filter(candidate -> candidate.supports(request.item(), request.file()))
                .min(java.util.Comparator.comparing(TransformationRule::ruleId))
                .orElseThrow(() -> new TransformationUnsupportedException(
                        "No vetted transformation rule supports this item."));
        TransformationDraft draft;
        try {
            draft = rule.propose(request);
        } catch (TransformationUnsupportedException | TransformationInvalidException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TransformationInvalidException(
                    "The original source could not be transformed by this rule.");
        }
        String proposed = draft.proposedText();
        if (proposed == null || proposed.isBlank()
                || (draft.artifactType() == dev.stravique.domain.TransformationArtifactType.EDIT_FILE
                && proposed.equals(request.source()))) {
            throw new TransformationUnsupportedException(
                    "The rule found no eligible construct to transform.");
        }
        try {
            rule.validate(proposed);
        } catch (TransformationInvalidException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TransformationInvalidException(
                    "The transformed source is not syntactically valid.");
        }
        String original = draft.artifactType()
                == dev.stravique.domain.TransformationArtifactType.CREATE_FILE
                ? "" : request.source();
        String diff = diff(original, proposed, draft.targetRelativePath(),
                draft.artifactType() == dev.stravique.domain.TransformationArtifactType.CREATE_FILE);
        return new TransformationOutcome(rule.ruleId(), draft.artifactType(),
                draft.targetRelativePath(), rule.ruleVersion(), proposed, diff, "PARSED", null,
                rule.proposedEncoding());
    }

    private String diff(String original, String proposed, String relativePath, boolean create) {
        List<String> originalLines = List.of(original.split("\\R", -1));
        List<String> proposedLines = List.of(proposed.split("\\R", -1));
        Patch<String> patch = DiffUtils.diff(originalLines, proposedLines);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
                create ? "/dev/null" : "a/" + relativePath,
                "b/" + relativePath, originalLines, patch, 3);
        return String.join("\n", unified);
    }
}
