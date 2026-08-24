package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.OptionalDouble;

public record GamePerformanceComparison(GamePerformanceResult before, GamePerformanceResult after) {
    public OptionalDouble averageFpsChangePercent() { return change(before.averageFps(), after.averageFps()); }
    public OptionalDouble onePercentLowChangePercent() { return change(before.onePercentLow(), after.onePercentLow()); }
    public OptionalDouble frameTimeChangePercent() { return change(before.averageFrameTimeMs(), after.averageFrameTimeMs()); }
    public String interpretation() {
        OptionalDouble change = averageFpsChangePercent();
        if (change.isEmpty() || Math.abs(change.getAsDouble()) < 1.0) return "No meaningful difference detected.";
        if (change.getAsDouble() > 0 && change.getAsDouble() < 5.0) return "Small measurable improvement.";
        if (change.getAsDouble() >= 5.0) return "Measurable improvement detected.";
        return "Measured performance decreased.";
    }
    private static OptionalDouble change(OptionalDouble before, OptionalDouble after) {
        if (before.isEmpty() || after.isEmpty() || before.getAsDouble() == 0) return OptionalDouble.empty();
        return OptionalDouble.of((after.getAsDouble() - before.getAsDouble()) / before.getAsDouble() * 100.0);
    }
}
