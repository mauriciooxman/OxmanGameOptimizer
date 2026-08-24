package cl.oxman.oxmangameoptimizer.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceLabStateMachineTest {
    @Test void acceptsWorkflowAndRejectsImpossibleTransition() {
        var machine = new PerformanceLabStateMachine();
        machine.transitionTo(PerformanceLabState.MEASURING_BASELINE);
        machine.transitionTo(PerformanceLabState.WAITING_FOR_GAME);
        assertThrows(IllegalStateException.class, () -> machine.transitionTo(PerformanceLabState.COMPLETED));
    }
    @Test void acceptsBenchmarkOrchestrationFlow() {
        var machine = new PerformanceLabStateMachine();
        machine.transitionTo(PerformanceLabState.WAITING_FOR_GAME);
        machine.transitionTo(PerformanceLabState.CAPTURING);
        machine.transitionTo(PerformanceLabState.APPLYING_BOOST);
        machine.transitionTo(PerformanceLabState.STABILIZING);
        machine.transitionTo(PerformanceLabState.CAPTURING_OPTIMIZED);
        machine.transitionTo(PerformanceLabState.ANALYZING);
        machine.transitionTo(PerformanceLabState.COMPLETED);
    }
}
