package cl.oxman.oxmangameoptimizer.optimizer;

public record OptimizationReport(int applicable, int applied, int failed, boolean fullyRestored,
        java.util.List<cl.oxman.oxmangameoptimizer.optimizer.action.ActionOutcome> outcomes) {
    public OptimizationReport { outcomes = java.util.List.copyOf(outcomes); }
    public OptimizationReport(int applicable, int applied, int failed, boolean fullyRestored) {
        this(applicable, applied, failed, fullyRestored, java.util.List.of());
    }
    public OptimizationReport(int applicable, int applied, boolean fullyRestored) {
        this(applicable, applied, 0, fullyRestored);
    }
    public int omitted() { return Math.max(0, applicable - applied - failed); }
    public boolean hasCriticalFailure() { return applicable == 0 && failed > 0; }
}
