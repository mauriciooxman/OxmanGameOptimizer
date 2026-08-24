package cl.oxman.oxmangameoptimizer.optimizer;

public record OptimizationReport(int applicable, int applied, int failed, boolean fullyRestored) {
    public OptimizationReport(int applicable, int applied, boolean fullyRestored) {
        this(applicable, applied, 0, fullyRestored);
    }
    public int omitted() { return Math.max(0, applicable - applied - failed); }
    public boolean hasCriticalFailure() { return applicable == 0 && failed > 0; }
}
