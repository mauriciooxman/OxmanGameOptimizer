package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record OptimizationPlan(Set<String> actionIds, List<OptimizationAssessmentItem> observations) {
    public OptimizationPlan { actionIds = Set.copyOf(actionIds); observations = List.copyOf(observations); }
    public static OptimizationPlan from(SystemOptimizationAssessment assessment) {
        Set<String> safeActions = assessment.items().stream()
                .filter(item -> item.status() == AssessmentStatus.ACTION_AVAILABLE)
                .filter(item -> item.safety() == OptimizationSafety.SAFE)
                .map(OptimizationAssessmentItem::actionId)
                .collect(Collectors.toUnmodifiableSet());
        return new OptimizationPlan(safeActions, assessment.items());
    }
}
