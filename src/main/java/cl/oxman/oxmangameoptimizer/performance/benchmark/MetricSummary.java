package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

public record MetricSummary(OptionalDouble mean, OptionalDouble median, OptionalDouble standardDeviation, int count) {
    public static MetricSummary of(List<OptionalDouble> metrics) {
        double[] values = metrics.stream().filter(OptionalDouble::isPresent).mapToDouble(OptionalDouble::getAsDouble).toArray();
        if (values.length == 0) return new MetricSummary(OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), 0);
        double mean = Arrays.stream(values).average().orElseThrow();
        double[] sorted = values.clone(); Arrays.sort(sorted);
        double median = sorted.length % 2 == 1 ? sorted[sorted.length / 2]
                : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;
        double variance = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / values.length;
        return new MetricSummary(OptionalDouble.of(mean), OptionalDouble.of(median),
                OptionalDouble.of(Math.sqrt(variance)), values.length);
    }
    public double coefficientOfVariation() {
        return mean.isPresent() && mean.getAsDouble() != 0 && standardDeviation.isPresent()
                ? Math.abs(standardDeviation.getAsDouble() / mean.getAsDouble()) : 0;
    }
}
