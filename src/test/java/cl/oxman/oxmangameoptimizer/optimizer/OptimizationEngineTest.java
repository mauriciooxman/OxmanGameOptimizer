package cl.oxman.oxmangameoptimizer.optimizer;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationResult;
import cl.oxman.oxmangameoptimizer.optimizer.state.SessionStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptimizationEngineTest {
    @TempDir Path temporary;

    @Test
    void restoresAppliedActionsInReverseOrder() {
        List<String> events = new ArrayList<>();
        FakeAction first = new FakeAction("first", events, true);
        FakeAction second = new FakeAction("second", events, true);
        SessionStateStore store = new SessionStateStore(temporary.resolve("state.json"));
        OptimizationEngine engine = new OptimizationEngine(List.of(first, second), store, ignored -> { });

        assertEquals(2, engine.apply("game").applied());
        assertTrue(engine.restore().fullyRestored());
        assertEquals(List.of("apply:first", "apply:second", "restore:second:old-second",
                "restore:first:old-first"), events);
        assertFalse(engine.hasIncompleteSession());
    }

    @Test
    void failedActionIsNotAddedToRecoverySnapshot() {
        List<String> events = new ArrayList<>();
        FakeAction failed = new FakeAction("failed", events, false);
        FakeAction applied = new FakeAction("applied", events, true);
        OptimizationEngine engine = new OptimizationEngine(List.of(failed, applied),
                new SessionStateStore(temporary.resolve("state.json")), ignored -> { });

        assertEquals(1, engine.apply("game").applied());
        assertTrue(engine.restore().fullyRestored());
        assertFalse(events.stream().anyMatch(value -> value.startsWith("restore:failed")));
    }

    @Test void plannedApplyRunsOnlyActionsThatNeedChanges() {
        List<String> events = new ArrayList<>();
        FakeAction needed = new FakeAction("needed", events, true);
        FakeAction optimized = new FakeAction("optimized", events, true);
        OptimizationEngine engine = new OptimizationEngine(List.of(needed, optimized),
                new SessionStateStore(temporary.resolve("planned.json")), ignored -> { });

        var report = engine.apply("game", java.util.Set.of("needed"));
        assertEquals(1, report.applicable());
        assertEquals(1, report.applied());
        assertEquals(List.of("apply:needed"), events);
    }

    @Test void emptyPlanRepresentsNoChangeWithoutApplyingAnything() {
        List<String> events = new ArrayList<>();
        OptimizationEngine engine = new OptimizationEngine(List.of(new FakeAction("safe", events, true)),
                new SessionStateStore(temporary.resolve("no-change.json")), ignored -> { });
        var report = engine.apply("game", java.util.Set.of());
        assertEquals(0, report.applicable());
        assertEquals(0, report.applied());
        assertTrue(events.isEmpty());
    }

    @Test void addsDeferredActionToSameJournalAndAvoidsDuplicateApplication() {
        List<String> events = new ArrayList<>();
        FakeAction power = new FakeAction("power", events, true);
        FakeAction priority = new FakeAction("priority-high:42", events, true);
        OptimizationEngine engine = new OptimizationEngine(List.of(power),
                new SessionStateStore(temporary.resolve("deferred.json")), ignored -> { });

        assertEquals(1, engine.apply("CS2").applied());
        assertEquals(1, engine.applyAdditional("CS2", priority).applied());
        assertEquals(0, engine.applyAdditional("CS2", priority).applied());
        assertTrue(engine.restore().fullyRestored());
        assertEquals(List.of("apply:power", "apply:priority-high:42",
                "restore:priority-high:42:old-priority-high:42", "restore:power:old-power"), events);
    }

    @Test void persistedDeferredActionRecoversAfterUnexpectedShutdown() {
        List<String> events = new ArrayList<>();
        FakeAction power = new FakeAction("power", events, true);
        FakeAction priority = new FakeAction("priority-high:42", events, true);
        SessionStateStore store = new SessionStateStore(temporary.resolve("crash.json"));
        OptimizationEngine active = new OptimizationEngine(List.of(power), store, ignored -> { });
        active.apply("CS2");
        active.applyAdditional("CS2", priority);

        OptimizationEngine recovered = new OptimizationEngine(List.of(power, priority), store, ignored -> { });
        assertTrue(recovered.restore().fullyRestored());
        assertFalse(recovered.hasIncompleteSession());
        assertEquals(List.of("apply:power", "apply:priority-high:42",
                "restore:priority-high:42:old-priority-high:42", "restore:power:old-power"), events);
    }

    @Test void partialFailureDoesNotPreventLaterIndependentAction() {
        List<String> events = new ArrayList<>();
        FakeAction failedPower = new FakeAction("power", events, false);
        FakeAction priority = new FakeAction("priority-high:42", events, true);
        OptimizationEngine engine = new OptimizationEngine(List.of(failedPower),
                new SessionStateStore(temporary.resolve("partial.json")), ignored -> { });

        assertEquals(1, engine.apply("CS2").failed());
        assertEquals(1, engine.applyAdditional("CS2", priority).applied());
        assertTrue(engine.restore().fullyRestored());
        assertEquals(List.of("apply:power", "apply:priority-high:42",
                "restore:priority-high:42:old-priority-high:42"), events);
    }

    private static final class FakeAction implements OptimizationAction {
        private final String id;
        private final List<String> events;
        private final boolean succeeds;
        private FakeAction(String id, List<String> events, boolean succeeds) {
            this.id = id; this.events = events; this.succeeds = succeeds;
        }
        @Override public String id() { return id; }
        @Override public String name() { return id; }
        @Override public String description() { return id; }
        @Override public boolean isSupported() { return true; }
        @Override public boolean requiresAdministrator() { return false; }
        @Override public boolean isReversible() { return true; }
        @Override public OptimizationResult apply() {
            events.add("apply:" + id);
            return succeeds ? OptimizationResult.changed("done") : OptimizationResult.failure("failed");
        }
        @Override public OptimizationResult restore(String originalState) {
            events.add("restore:" + id + ":" + originalState);
            return OptimizationResult.changed("restored");
        }
        @Override public String originalState() { return "old-" + id; }
    }
}
