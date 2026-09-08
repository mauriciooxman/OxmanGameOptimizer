package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import java.util.List;

public record SystemOptimizationAssessment(List<OptimizationAssessmentItem> items, PreGameBaseline baseline,
        List<PerformanceOpportunity> opportunities,
        List<cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation> candidates) {
    public SystemOptimizationAssessment {
        items = List.copyOf(items); opportunities = List.copyOf(opportunities); candidates = List.copyOf(candidates);
    }
    public SystemOptimizationAssessment(List<OptimizationAssessmentItem> items) {
        this(items, PreGameBaseline.unavailable(), List.of(), List.of());
    }
    public boolean alreadyOptimized() {
        return items.stream().noneMatch(item -> item.status() == AssessmentStatus.ACTION_AVAILABLE
                && item.safety() == cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety.SAFE);
    }
}
