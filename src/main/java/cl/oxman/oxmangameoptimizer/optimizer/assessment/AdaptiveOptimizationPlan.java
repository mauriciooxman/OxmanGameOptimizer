package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.BackgroundEcoQosAction;
import cl.oxman.oxmangameoptimizer.optimizer.action.PowerPlanAction;
import cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Reuses the SAFE plan filter and narrows it to the explicitly approved adaptive actions. */
public record AdaptiveOptimizationPlan(OptimizationPlan plan, List<BackgroundProcessObservation> backgroundCandidates) {
    public AdaptiveOptimizationPlan { backgroundCandidates = List.copyOf(backgroundCandidates); }
    public Set<String> actionIds() { return plan.actionIds(); }
    public static AdaptiveOptimizationPlan from(SystemOptimizationAssessment assessment) {
        Set<String> eligibleIds = assessment.candidates().stream()
                .limit(cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessSelector.MAXIMUM_PROCESSES)
                .map(p -> BackgroundEcoQosAction.ID_PREFIX + p.identity().pid()).collect(Collectors.toSet());
        var existing = OptimizationPlan.from(assessment);
        var ids = existing.actionIds().stream().filter(id -> id.equals(PowerPlanAction.ID) || eligibleIds.contains(id))
                .collect(Collectors.toUnmodifiableSet());
        return new AdaptiveOptimizationPlan(new OptimizationPlan(ids, assessment.items()), assessment.candidates().stream()
                .filter(p -> ids.contains(BackgroundEcoQosAction.ID_PREFIX + p.identity().pid()))
                .limit(cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessSelector.MAXIMUM_PROCESSES).toList());
    }
}
