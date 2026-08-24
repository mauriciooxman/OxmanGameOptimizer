package cl.oxman.oxmangameoptimizer.performance.benchmark;

public enum PerformanceInterpretation {
    NO_OPTIMIZATIONS_APPLIED("No se aplicaron optimizaciones. Las diferencias observadas pueden corresponder a variación normal del juego."),
    NO_MEANINGFUL_DIFFERENCE("No meaningful difference detected."),
    SMALL_IMPROVEMENT("Small measurable improvement."),
    MEASURABLE_IMPROVEMENT("Measurable improvement detected."),
    REGRESSION("Performance regression detected.");
    private final String message;
    PerformanceInterpretation(String message) { this.message = message; }
    public String message() { return message; }

    public static PerformanceInterpretation from(GamePerformanceComparison comparison) {
        double fps = comparison.averageFpsChangePercent().orElse(0);
        double low = comparison.onePercentLowChangePercent().orElse(0);
        double frame = comparison.frameTimeChangePercent().orElse(0);
        if (fps < -2 || low < -3 || frame > 3) return REGRESSION;
        if (fps >= 5 || low >= 7 || frame <= -5) return MEASURABLE_IMPROVEMENT;
        if (fps >= 1 || low >= 2 || frame <= -1) return SMALL_IMPROVEMENT;
        return NO_MEANINGFUL_DIFFERENCE;
    }

    public static PerformanceInterpretation from(GamePerformanceComparison comparison, int appliedOptimizations) {
        return appliedOptimizations == 0 ? NO_OPTIMIZATIONS_APPLIED : from(comparison);
    }
}
