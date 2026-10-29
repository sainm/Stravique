package dev.stravique.transformation;

import dev.stravique.domain.MigrationItem;
import dev.stravique.domain.MigrationItemType;
import dev.stravique.domain.MigrationTarget;
import dev.stravique.domain.ProjectFile;
import dev.stravique.domain.TransformationArtifactType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationEngineTest {

    @Test
    void picksTheLowestRuleIdWhateverOrderTheRulesAreRegisteredIn() {
        TransformationEngine engine = new TransformationEngine(
                List.of(new StubRule("Z-RULE"), new StubRule("A-RULE")));

        TransformationOutcome outcome = engine.propose(request("class Order {}\n"));

        assertThat(outcome.ruleId()).isEqualTo("A-RULE");
    }

    @Test
    void refusesWhenNoRuleSupportsTheItem() {
        TransformationEngine engine = new TransformationEngine(List.of(new StubRule("A-RULE", false)));

        assertThatThrownBy(() -> engine.propose(request("class Order {}\n")))
                .isInstanceOf(TransformationUnsupportedException.class);
    }

    private TransformationRequest request(String source) {
        return new TransformationRequest(null, null, source, "src/Order.java", List.of());
    }

    private record StubRule(String ruleId, boolean supported) implements TransformationRule {

        private StubRule(String ruleId) {
            this(ruleId, true);
        }

        @Override
        public String ruleVersion() {
            return "1.0.0";
        }

        @Override
        public MigrationItemType itemType() {
            return MigrationItemType.COMPONENT_BEAN;
        }

        @Override
        public MigrationTarget target() {
            return MigrationTarget.SPRING_DI;
        }

        @Override
        public boolean supports(MigrationItem item, ProjectFile file) {
            return supported;
        }

        @Override
        public TransformationDraft propose(TransformationRequest request) {
            return new TransformationDraft(TransformationArtifactType.EDIT_FILE,
                    request.relativePath(), request.source() + "// " + ruleId + "\n");
        }
    }
}
