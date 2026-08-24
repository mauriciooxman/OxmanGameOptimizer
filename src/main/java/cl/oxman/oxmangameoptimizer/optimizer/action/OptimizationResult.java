package cl.oxman.oxmangameoptimizer.optimizer.action;

public record OptimizationResult(boolean success, boolean changed, String detail, String error) {
    public static OptimizationResult changed(String detail) {
        return new OptimizationResult(true, true, detail, null);
    }

    public static OptimizationResult unchanged(String detail) {
        return new OptimizationResult(true, false, detail, null);
    }

    public static OptimizationResult failure(String error) {
        return new OptimizationResult(false, false, null, error);
    }
}
