package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.optimizer.action.OptimizationSafety;

public record OptimizationAssessmentItem(String actionId, String label, AssessmentStatus status,
        OptimizationSafety safety, String detail) { }
