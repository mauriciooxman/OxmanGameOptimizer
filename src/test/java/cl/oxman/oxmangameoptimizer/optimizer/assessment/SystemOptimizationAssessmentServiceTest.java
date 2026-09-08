package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.*;
import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import cl.oxman.oxmangameoptimizer.system.*;
import cl.oxman.oxmangameoptimizer.ui.DiagnosisPresentation;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SystemOptimizationAssessmentServiceTest {
    private static final String HIGH = PowerPlanPolicy.HIGH;
    private static final String BALANCED = "381b4222-f694-41f0-9685-ff5bb260df2e";
    private static final ProcessIdentity GAME = new ProcessIdentity(99999, 100, "cs2.exe");
    private static SystemOptimizationAssessment assess(String power, double ram, List<BackgroundProcessObservation> processes, FakeQos qos) {
        var service = new SystemOptimizationAssessmentService((c, t) -> new CommandResult(0,
                c.contains("/list") ? HIGH : power, false));
        return service.assess(new PreGameBaseline(4, 15, ram, 4_000_000_000L, 150, processes, true), GAME, qos);
    }
    private static BackgroundProcessObservation process(long pid, String name, double cpu) {
        return new BackgroundProcessObservation(new ProcessIdentity(pid, 100, name), "test", true, false, cpu);
    }
    @Test void optimizedPcHasEmptyPlan() {
        var a = assess(HIGH, 50, List.of(), new FakeQos());
        assertTrue(AdaptiveOptimizationPlan.from(a).actionIds().isEmpty());
        assertTrue(a.opportunities().stream().anyMatch(o -> o.id().equals("ALREADY_OPTIMIZED")));
    }
    @Test void balancedPlanHasOnlyPowerAction() {
        assertEquals(Set.of("power-plan"), AdaptiveOptimizationPlan.from(assess(BALANCED, 50, List.of(), new FakeQos())).actionIds());
    }
    @Test void ultimatePlanIsEquivalentAndCustomPlanIsPreserved() {
        for (String guid : List.of("e9a42b02-d5df-448d-aa00-03f14749eb61", "12345678-1234-1234-1234-123456789abc"))
            assertTrue(AdaptiveOptimizationPlan.from(assess(guid, 50, List.of(), new FakeQos())).actionIds().isEmpty());
    }
    @Test void unavailableHighPerformanceDoesNotCreateAction() {
        var service = new SystemOptimizationAssessmentService((c,t) -> new CommandResult(0, BALANCED, false));
        assertTrue(AdaptiveOptimizationPlan.from(service.assess()).actionIds().isEmpty());
    }
    @Test void highBackgroundLoadProducesSafeBackgroundAction() {
        var a = assess(HIGH, 50, List.of(process(101, "OneDrive.exe", 4)), new FakeQos());
        assertEquals(Set.of("background-ecoqos:101"), AdaptiveOptimizationPlan.from(a).actionIds());
        assertTrue(a.items().stream().filter(i -> AdaptiveOptimizationPlan.from(a).actionIds().contains(i.actionId()))
                .allMatch(i -> i.safety() == OptimizationSafety.SAFE));
    }
    @Test void halfPercentOneDriveDoesNotJustifyAction() {
        assertTrue(AdaptiveOptimizationPlan.from(assess(HIGH, 50, List.of(process(101, "OneDrive.exe", .5)), new FakeQos())).actionIds().isEmpty());
    }
    @Test void thresholdUsesAggregateOfAtMostThreeProcesses() {
        var p = List.of(process(101,"chrome.exe",1),process(102,"firefox.exe",1),process(103,"onedrive.exe",1),process(104,"dropbox.exe",1));
        var plan = AdaptiveOptimizationPlan.from(assess(HIGH,50,p,new FakeQos()));
        assertEquals(3, plan.actionIds().size());
        assertEquals(3, plan.backgroundCandidates().size());
        var below = p.stream().map(v -> process(v.identity().pid(), v.identity().processName(), .9)).toList();
        assertTrue(AdaptiveOptimizationPlan.from(assess(HIGH,50,below,new FakeQos())).actionIds().isEmpty());
    }
    @Test void unknownForegroundOtherUserGameAndOwnProcessesAreExcluded() {
        var foreground = new BackgroundProcessObservation(new ProcessIdentity(102,100,"chrome.exe"),"test",true,true,20);
        var otherUser = new BackgroundProcessObservation(new ProcessIdentity(103,100,"chrome.exe"),"other",false,false,20);
        var p = List.of(process(101,"unknown.exe",20), foreground, otherUser,
                process(GAME.pid(),"chrome.exe",20),process(ProcessHandle.current().pid(),"chrome.exe",20));
        assertTrue(AdaptiveOptimizationPlan.from(assess(HIGH,50,p,new FakeQos())).actionIds().isEmpty());
    }
    @Test void alreadyEcoQosCandidatesDoNotInflateActionableLoad() {
        var qos = new FakeQos(); qos.states.put(101L,new ProcessPowerThrottlingState(1,1,1));
        var p = List.of(process(101,"chrome.exe",10),process(102,"onedrive.exe",.5));
        assertTrue(AdaptiveOptimizationPlan.from(assess(HIGH,50,p,qos)).actionIds().isEmpty());
    }
    @Test void highQosActiveIsOptimizedAndPriorityNeverPromoted() {
        var a = assess(HIGH,50,List.of(),new FakeQos());
        assertEquals(AssessmentStatus.OPTIMIZED, a.items().stream().filter(i -> i.actionId().equals("high-qos")).findFirst().orElseThrow().status());
        assertFalse(AdaptiveOptimizationPlan.from(a).actionIds().contains("process-priority"));
        assertFalse(AdaptiveOptimizationPlan.from(a).actionIds().contains("high-qos"));
    }
    @Test void memoryBoundariesAreExplicitAndDoNotTriggerTweaks() {
        var t = DiagnosisThresholds.DEFAULT;
        assertEquals(PerformanceOpportunity.Severity.OK,t.memorySeverity(74.99));
        assertEquals(PerformanceOpportunity.Severity.NOTICE,t.memorySeverity(75));
        assertEquals(PerformanceOpportunity.Severity.NOTICE,t.memorySeverity(85));
        assertEquals(PerformanceOpportunity.Severity.HIGH_PRESSURE,t.memorySeverity(85.01));
        assertEquals(PerformanceOpportunity.Severity.UNKNOWN,t.memorySeverity(Double.NaN));
        assertTrue(AdaptiveOptimizationPlan.from(assess(HIGH,95,List.of(),new FakeQos())).actionIds().isEmpty());
    }
    @Test void unavailableBaselineDoesNotClaimReadyOrSelectBackground() {
        var service = new SystemOptimizationAssessmentService((c,t) -> new CommandResult(0,HIGH,false));
        var a = service.assess(PreGameBaseline.unavailable(), null, new FakeQos());
        assertTrue(AdaptiveOptimizationPlan.from(a).actionIds().isEmpty());
        assertFalse(DiagnosisPresentation.summary(new OptimizationReport(0,0,0,true),a).contains("SISTEMA LISTO"));
    }
    @Test void customPowerPlanDoesNotClaimKnownReady() {
        var a = assess("12345678-1234-1234-1234-123456789abc",50,List.of(),new FakeQos());
        assertFalse(DiagnosisPresentation.summary(new OptimizationReport(0,0,0,true),a).contains("SISTEMA LISTO"));
    }
    @Test void summaryCountsAppliedSeparatelyFromAlreadyOptimizedAndFailure() {
        var a = assess(HIGH,50,List.of(),new FakeQos());
        String unchanged = DiagnosisPresentation.summary(new OptimizationReport(2,0,0,false),a);
        assertTrue(unchanged.contains("0 cambios aplicados"));
        assertTrue(DiagnosisPresentation.summary(new OptimizationReport(2,1,0,false),a).contains("1 cambio aplicado"));
        assertFalse(DiagnosisPresentation.summary(new OptimizationReport(2,1,1,false),a).contains("Sistema preparado"));
        assertFalse(DiagnosisPresentation.summary(new OptimizationReport(0,0,0,true),assess(HIGH,95,List.of(),new FakeQos())).contains("SISTEMA LISTO"));
    }
    static final class FakeQos implements ProcessPowerThrottlingService {
        final Map<Long,ProcessPowerThrottlingState> states = new HashMap<>();
        public ProcessIdentity identify(long pid) { return new ProcessIdentity(pid,100,"test.exe"); }
        public ProcessPowerThrottlingState read(ProcessIdentity p) { return states.getOrDefault(p.pid(), new ProcessPowerThrottlingState(1,1,0)); }
        public void applyHighQos(ProcessIdentity p) { fail("HighQoS must not be applied"); }
        public void applyEcoQos(ProcessIdentity p,ProcessPowerThrottlingState s) { states.put(p.pid(),s.withExecutionSpeedEnabled()); }
        public void restore(ProcessIdentity p,ProcessPowerThrottlingState s) { states.put(p.pid(),s); }
        public boolean isSameProcess(ProcessIdentity p) { return true; }
    }
}
