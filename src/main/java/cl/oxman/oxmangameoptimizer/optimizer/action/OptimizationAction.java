package cl.oxman.oxmangameoptimizer.optimizer.action;

public interface OptimizationAction {
    String id();
    String name();
    String description();
    boolean isSupported();
    boolean requiresAdministrator();
    boolean isReversible();
    default OptimizationSafety safety() { return OptimizationSafety.SAFE; }
    OptimizationResult apply();
    OptimizationResult restore(String originalState);
    String originalState();
}
