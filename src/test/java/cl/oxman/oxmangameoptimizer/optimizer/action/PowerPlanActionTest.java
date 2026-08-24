package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.system.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class PowerPlanActionTest {
    @Test
    void capturesVerifiesAndRestoresOriginalGuid() {
        String balanced = "381b4222-f694-41f0-9685-ff5bb260df2e";
        String high = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
        Queue<CommandResult> results = new ArrayDeque<>();
        results.add(ok("Power Scheme GUID: " + balanced + " (Balanced)"));
        results.add(ok(""));
        results.add(ok("Power Scheme GUID: " + high + " (High performance)"));
        results.add(ok("Power Scheme GUID: " + high));
        results.add(ok(""));
        results.add(ok("Power Scheme GUID: " + balanced));
        PowerPlanAction action = new PowerPlanAction((command, timeout) -> results.remove());

        assertTrue(action.apply().success());
        assertEquals(balanced, action.originalState());
        assertTrue(action.restore(balanced).success());
        assertTrue(results.isEmpty());
    }

    private static CommandResult ok(String output) { return new CommandResult(0, output, false); }
}
