package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationPlanTest {
    @Test void planIncludesOnlyNecessarySafeActions() {
        var assessment = new SystemOptimizationAssessment(List.of(
                item("power-plan", AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.SAFE),
                item("already", AssessmentStatus.OPTIMIZED, OptimizationSafety.SAFE),
                item("process-priority", AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.EXPERIMENTAL)));
        assertEquals(java.util.Set.of("power-plan"), OptimizationPlan.from(assessment).actionIds());
    }

    @Test void noChangeProducesAnEmptyPlan() {
        var assessment = new SystemOptimizationAssessment(List.of(
                item("power-plan", AssessmentStatus.OPTIMIZED, OptimizationSafety.SAFE)));
        assertTrue(assessment.alreadyOptimized());
        assertTrue(OptimizationPlan.from(assessment).actionIds().isEmpty());
    }

    @Test void processPriorityAndHighQosAreNeverPromoted() {
        var assessment = new SystemOptimizationAssessment(List.of(
                item("process-priority", AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.EXPERIMENTAL),
                item("high-qos", AssessmentStatus.ACTION_AVAILABLE, OptimizationSafety.EXPERIMENTAL)));
        assertTrue(OptimizationPlan.from(assessment).actionIds().isEmpty());
    }

    private static OptimizationAssessmentItem item(String id, AssessmentStatus status, OptimizationSafety safety) {
        return new OptimizationAssessmentItem(id, id, status, safety, "detail");
    }
}
