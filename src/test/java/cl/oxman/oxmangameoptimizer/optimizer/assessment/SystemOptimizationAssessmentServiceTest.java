package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SystemOptimizationAssessmentServiceTest {
    @Test void detectsAlreadyOptimizedExistingStates() {
        var service = new SystemOptimizationAssessmentService((command, timeout) ->
                command.get(0).equals("powercfg")
                        ? new CommandResult(0, "Power Scheme GUID: 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c", false)
                        : new CommandResult(0, "STATE : 1 STOPPED", false));
        var assessment = service.assess();
        assertEquals(AssessmentStatus.OPTIMIZED, assessment.items().get(0).status());
        assertEquals(AssessmentStatus.OPTIMIZED, assessment.items().get(1).status());
    }

    @Test void detectsSafeActionsThatAreActuallyNeeded() {
        var service = new SystemOptimizationAssessmentService((command, timeout) ->
                command.get(0).equals("powercfg")
                        ? new CommandResult(0, "Power Scheme GUID: 381b4222-f694-41f0-9685-ff5bb260df2e", false)
                        : new CommandResult(0, "STATE : 4 RUNNING", false));
        assertEquals(java.util.Set.of("power-plan", "service:DiagTrack"),
                OptimizationPlan.from(service.assess()).actionIds());
    }

    @Test void reportsBackgroundCandidatesWithoutPromotingThem() {
        var service = new SystemOptimizationAssessmentService((command, timeout) ->
                command.get(0).equals("powercfg")
                        ? new CommandResult(0, "Power Scheme GUID: 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c", false)
                        : new CommandResult(0, "STATE : 1 STOPPED", false), () -> 2);
        var assessment = service.assess();
        var background = assessment.items().stream().filter(item -> item.actionId().equals("background-load")).findFirst().orElseThrow();
        assertEquals(AssessmentStatus.ACTION_AVAILABLE, background.status());
        assertFalse(OptimizationPlan.from(assessment).actionIds().contains("background-load"));
    }
}
