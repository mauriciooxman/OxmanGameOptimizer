package cl.oxman.oxmangameoptimizer.optimizer.assessment;

public record PerformanceOpportunity(String id, Category category, Severity severity, AssessmentStatus status,
        String description, String recommendedAction, EstimatedImpact estimatedImpact) {
    public enum Category { POWER, CPU, RAM, BACKGROUND, GAME, SYSTEM }
    public enum Severity { OK, NOTICE, HIGH_PRESSURE, UNKNOWN }
    public enum EstimatedImpact { UNKNOWN, LOW, MODERATE }
}
