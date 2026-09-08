package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.*;
import cl.oxman.oxmangameoptimizer.optimizer.action.*;
import cl.oxman.oxmangameoptimizer.optimizer.state.*;
import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import cl.oxman.oxmangameoptimizer.system.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveExecutionTest {
    @TempDir Path temporary;
    @Test void safePromotionAppliesAndCanRecoverWithNewEngine() throws Exception {
        var candidate = new BackgroundProcessObservation(new ProcessIdentity(123,100,"onedrive.exe"),"test",true,false,4);
        var qos = new SystemOptimizationAssessmentServiceTest.FakeQos();
        var assessment = new SystemOptimizationAssessmentService((c,t) -> new CommandResult(0,PowerPlanPolicy.HIGH,false))
                .assess(new PreGameBaseline(4,20,50,10000,100,List.of(candidate),true),null,qos);
        var plan = AdaptiveOptimizationPlan.from(assessment);
        var actions = AdaptiveBackgroundAction.create(assessment,plan,DiagnosisThresholds.DEFAULT,qos,d -> List.of(candidate),-1,s -> {});
        assertEquals(1,actions.size());
        assertEquals(OptimizationSafety.SAFE,actions.getFirst().safety());
        var store = new SessionStateStore(temporary.resolve("state.json"));
        var report = new OptimizationEngine(actions,store,s -> {}).apply("game",plan.actionIds());
        assertEquals(1,report.applied());
        assertEquals(ActionOutcome.Status.APPLIED,report.outcomes().getFirst().status());
        assertTrue(qos.read(candidate.identity()).isExecutionSpeedEnabled());
        assertEquals(1,store.load().orElseThrow().changes().size());
        var recovery = new OptimizationEngine(List.of(new BackgroundEcoQosAction(candidate.identity(),qos,s -> {})),store,s -> {});
        assertTrue(recovery.restore().fullyRestored());
        assertFalse(qos.read(candidate.identity()).isExecutionSpeedEnabled());
    }
    @Test void foregroundChangePreventsMutation() {
        var candidate = new BackgroundProcessObservation(new ProcessIdentity(123,100,"onedrive.exe"),"test",true,false,4);
        var qos = new SystemOptimizationAssessmentServiceTest.FakeQos();
        var assessment = new SystemOptimizationAssessmentService((c,t) -> new CommandResult(0,PowerPlanPolicy.HIGH,false))
                .assess(new PreGameBaseline(4,20,50,10000,100,List.of(candidate),true),null,qos);
        var plan = AdaptiveOptimizationPlan.from(assessment);
        var actions = AdaptiveBackgroundAction.create(assessment,plan,DiagnosisThresholds.DEFAULT,qos,
                d -> List.of(new BackgroundProcessObservation(candidate.identity(),"test",true,true,10)),-1,s -> {});
        var report = new OptimizationEngine(actions,new SessionStateStore(temporary.resolve("state.json")),s -> {}).apply("game",plan.actionIds());
        assertEquals(0,report.applied());
        assertEquals(ActionOutcome.Status.NOT_APPLICABLE,report.outcomes().getFirst().status());
    }
    @Test void journalExistsBeforeMutationAndFailureKeepsRemainingRollback() throws Exception {
        var store = new SessionStateStore(temporary.resolve("state.json"));
        List<String> restored = new ArrayList<>();
        List<OptimizationAction> actions = new ArrayList<>();
        for (String name : List.of("first","failed","last")) actions.add(new OptimizationAction() {
            public String id() { return name; }
            public String name() { return name; }
            public String description() { return name; }
            public boolean isSupported() { return true; }
            public boolean isReversible() { return true; }
            public boolean requiresAdministrator() { return false; }
            public String originalState() { return "original"; }
            public String prepareOriginalState() { return "original"; }
            public OptimizationResult apply() {
                try { assertTrue(store.load().orElseThrow().changes().stream().anyMatch(c -> c.actionId().equals(name))); }
                catch (java.io.IOException e) { throw new AssertionError(e); }
                return name.equals("failed") ? OptimizationResult.failure("native failure") : OptimizationResult.changed("verified");
            }
            public OptimizationResult restore(String state) { restored.add(name); return OptimizationResult.changed("restored"); }
        });
        var engine = new OptimizationEngine(actions,store,s -> {});
        var report = engine.apply("game");
        assertEquals(2,report.applied()); assertEquals(1,report.failed());
        assertEquals(3,store.load().orElseThrow().changes().size());
        assertTrue(engine.restore().fullyRestored());
        assertEquals(List.of("last","failed","first"),restored);
    }
    @Test void pendingSnapshotIsNotOverwrittenAndEmptyPlanCreatesNone() throws Exception {
        var store = new SessionStateStore(temporary.resolve("state.json"));
        var engine = new OptimizationEngine(List.of(),store,s -> {});
        engine.apply("empty",Set.of()); assertTrue(store.load().isEmpty());
        var previous = SessionState.begin("pending"); previous.add(new AppliedChange("missing","original")); store.save(previous);
        assertTrue(engine.apply("new",Set.of()).hasCriticalFailure());
        assertEquals("pending",store.load().orElseThrow().gameName());
        assertFalse(engine.restore().fullyRestored());
        assertTrue(store.load().isPresent());
    }
}
