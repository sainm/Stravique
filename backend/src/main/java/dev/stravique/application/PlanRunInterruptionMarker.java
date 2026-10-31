package dev.stravique.application;

import dev.stravique.domain.TransformationPlanRun;
import dev.stravique.domain.TransformationPlanRunStatus;
import dev.stravique.infrastructure.TransformationPlanRunRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PlanRunInterruptionMarker implements ApplicationRunner {

    private final TransformationPlanRunRepository planRunRepository;
    private final AuditService auditService;

    public PlanRunInterruptionMarker(TransformationPlanRunRepository planRunRepository,
                                     AuditService auditService) {
        this.planRunRepository = planRunRepository;
        this.auditService = auditService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (TransformationPlanRun run : planRunRepository.findByStatus(
                TransformationPlanRunStatus.RUNNING)) {
            run.markInterrupted();
            planRunRepository.save(run);
            auditService.record(run.getProjectId(), "TRANSFORMATION_PLAN_RUN_INTERRUPTED",
                    "TRANSFORMATION_PLAN_RUN", run.getId().toString(),
                    "process restart");
        }
    }
}
