package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.List;
import java.util.OptionalDouble;

public final class FrameStatistics {
    private FrameStatistics() { }
    /** 1% Low is 1000 divided by the mean frame time of the slowest one percent of valid frames. */
    public static OptionalDouble onePercentLowFps(List<Double> frameTimesMs) {
        List<Double> valid = frameTimesMs.stream().filter(value -> value != null && Double.isFinite(value) && value > 0).sorted().toList();
        if (valid.isEmpty()) return OptionalDouble.empty();
        int count = Math.max(1, (int) Math.ceil(valid.size() * 0.01));
        double worstAverage = valid.subList(valid.size() - count, valid.size()).stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return OptionalDouble.of(1000.0 / worstAverage);
    }
}
