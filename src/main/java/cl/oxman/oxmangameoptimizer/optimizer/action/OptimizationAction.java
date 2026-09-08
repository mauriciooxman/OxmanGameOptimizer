package cl.oxman.oxmangameoptimizer.optimizer.action;

public interface OptimizationAction {
    String id();
    String name();
    String description();
    boolean isSupported();
    boolean requiresAdministrator();
    boolean isReversible();
    default OptimizationSafety safety() { return OptimizationSafety.SAFE; }
    /** Optional write-ahead snapshot. Must read only; the engine persists it before apply. */
    default String prepareOriginalState() { return null; }
    OptimizationResult apply();
    OptimizationResult restore(String originalState);
    String originalState();
}
