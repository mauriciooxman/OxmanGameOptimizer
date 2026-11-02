package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationEngine;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import cl.oxman.oxmangameoptimizer.system.CommandResult;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import cl.oxman.oxmangameoptimizer.system.ProcessPriority;
import cl.oxman.oxmangameoptimizer.system.ProcessPriorityController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticBoostActionsTest {
    private static final String BALANCED = "381b4222-f694-41f0-9685-ff5bb260df2e";
    private static final String HIGH_PLAN = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    private static final ProcessIdentity CS2 = new ProcessIdentity(730, 1234, "cs2.exe");
    @TempDir Path temporary;

    @Test void oneJournalAppliesAndRestoresHighPriorityAndHighPerformance() {
        FakePower power = new FakePower(BALANCED);
        FakePriority priority = new FakePriority(ProcessPriority.NORMAL);
        OptimizationEngine engine = new OptimizationEngine(List.of(new PowerPlanAction(power)),
                new SessionStateStore(temporary.resolve("boost.json")), ignored -> { });

        assertEquals(1, engine.apply("Counter-Strike 2").applied());
        assertEquals(1, engine.applyAdditional("Counter-Strike 2",
                ProcessPriorityAction.high(CS2, priority, ignored -> { })).applied());
        assertEquals(HIGH_PLAN, power.active);
        assertEquals(ProcessPriority.HIGH, priority.value);

        assertTrue(engine.restore().fullyRestored());
        assertEquals(BALANCED, power.active);
        assertEquals(ProcessPriority.NORMAL, priority.value);
        assertFalse(engine.hasIncompleteSession());
    }

    @Test void restorationPreservesManualChangesMadeAfterBoost() {
        FakePower power = new FakePower(BALANCED);
        FakePriority priority = new FakePriority(ProcessPriority.NORMAL);
        OptimizationEngine engine = new OptimizationEngine(List.of(new PowerPlanAction(power)),
                new SessionStateStore(temporary.resolve("manual-change.json")), ignored -> { });
        engine.apply("Counter-Strike 2");
        engine.applyAdditional("Counter-Strike 2",
                ProcessPriorityAction.high(CS2, priority, ignored -> { }));

        String userPlan = "12345678-1234-1234-1234-123456789abc";
        power.active = userPlan;
        priority.value = ProcessPriority.ABOVE_NORMAL;

        assertTrue(engine.restore().fullyRestored());
        assertEquals(userPlan, power.active);
        assertEquals(ProcessPriority.ABOVE_NORMAL, priority.value);
    }

    private static final class FakePower implements cl.oxman.oxmangameoptimizer.system.SystemCommandRunner {
        private String active;
        private FakePower(String active) { this.active = active; }
        @Override public CommandResult run(List<String> command, java.time.Duration timeout) {
            if (command.contains("/getactivescheme"))
                return new CommandResult(0, "Power Scheme GUID: " + active, false);
            if (command.contains("/setactive")) {
                active = command.getLast();
                return new CommandResult(0, "", false);
            }
            return new CommandResult(1, "unsupported", false);
        }
    }

    private static final class FakePriority implements ProcessPriorityController {
        private ProcessPriority value;
        private FakePriority(ProcessPriority value) { this.value = value; }
        @Override public ProcessIdentity identify(long processId) { return CS2; }
        @Override public ProcessPriority read(ProcessIdentity process) { return value; }
        @Override public void set(ProcessIdentity process, ProcessPriority priority) { value = priority; }
        @Override public boolean isSameProcess(ProcessIdentity process) { return CS2.equals(process); }
    }
}
