package cl.oxman.oxmangameoptimizer.optimizer.action;

import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import cl.oxman.oxmangameoptimizer.optimizer.assessment.*;
import cl.oxman.oxmangameoptimizer.system.*;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** SAFE promotion is scoped to a complete baseline, allowlist and meaningful selected load. */
public final class AdaptiveBackgroundAction implements OptimizationAction {
    private final BackgroundEcoQosAction delegate;
    private final BackgroundProcessObservation candidate;
    private final BackgroundProcessSource source;
    private final long gamePid;
    private AdaptiveBackgroundAction(BackgroundProcessObservation candidate, ProcessPowerThrottlingService service,
            BackgroundProcessSource source, long gamePid, Consumer<String> log) {
        this.candidate = candidate; this.source = source; this.gamePid = gamePid;
        delegate = new BackgroundEcoQosAction(candidate.identity(), service, log);
    }
    public static List<OptimizationAction> create(SystemOptimizationAssessment assessment, AdaptiveOptimizationPlan plan,
            DiagnosisThresholds thresholds, ProcessPowerThrottlingService service, BackgroundProcessSource source,
            long gamePid, Consumer<String> log) {
        if (!assessment.baseline().complete() || assessment.baseline().sampleCount() < thresholds.samples()) return List.of();
        var selected = new BackgroundProcessSelector().select(plan.backgroundCandidates(), gamePid, ProcessHandle.current().pid());
        if (selected.stream().mapToDouble(BackgroundProcessObservation::cpuAveragePercent).sum() < thresholds.candidateAggregatePercent()) return List.of();
        return selected.stream().<OptimizationAction>map(p -> new AdaptiveBackgroundAction(p, service, source, gamePid, log)).toList();
    }
    private boolean stillEligible() {
        return source.observe(Duration.ofMillis(100)).stream().filter(p -> p.identity().equals(candidate.identity()))
                .map(p -> new BackgroundProcessObservation(p.identity(), p.user(), p.currentUser(), p.foreground(), candidate.cpuAveragePercent()))
                .anyMatch(p -> !new BackgroundProcessSelector().select(List.of(p), gamePid, ProcessHandle.current().pid()).isEmpty());
    }
    public String id() { return delegate.id(); }
    public String name() { return "Carga de fondo: " + candidate.identity().processName(); }
    public String description() { return delegate.description(); }
    public boolean isSupported() { return delegate.isSupported() && stillEligible(); }
    public boolean requiresAdministrator() { return false; }
    public boolean isReversible() { return true; }
    public String prepareOriginalState() { return delegate.prepareOriginalState(); }
    public String originalState() { return delegate.originalState(); }
    public OptimizationResult apply() {
        if (!stillEligible()) return OptimizationResult.unchanged("CONFIGURATION_DRIFT: candidato ya no elegible");
        return delegate.apply();
    }
    public OptimizationResult restore(String state) { return delegate.restore(state); }
}
