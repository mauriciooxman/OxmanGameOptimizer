package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import java.util.List;

public record SystemOptimizationAssessment(List<OptimizationAssessmentItem> items) {
    public SystemOptimizationAssessment { items = List.copyOf(items); }
    public boolean alreadyOptimized() {
        return items.stream().noneMatch(item -> item.status() == AssessmentStatus.ACTION_AVAILABLE
                && item.safety() == cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety.SAFE);
    }
}
