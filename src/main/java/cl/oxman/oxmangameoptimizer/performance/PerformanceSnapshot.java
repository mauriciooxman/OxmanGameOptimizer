package cl.oxman.oxmangameoptimizer.performance;

import java.time.Instant;
import java.util.List;

public record PerformanceSnapshot(Instant timestamp, double cpuAverage, double cpuMinimum,
        double cpuMaximum, double ramUsedAverage, double ramAvailableAverage,
        double processCountAverage, String activePowerPlan, int sampleCount) {
    public static PerformanceSnapshot from(List<PerformanceSample> samples, String powerPlan) {
        if (samples.isEmpty()) return new PerformanceSnapshot(Instant.now(), Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, powerPlan, 0);
        return new PerformanceSnapshot(Instant.now(),
                samples.stream().mapToDouble(PerformanceSample::cpuPercent).average().orElseThrow(),
                samples.stream().mapToDouble(PerformanceSample::cpuPercent).min().orElseThrow(),
                samples.stream().mapToDouble(PerformanceSample::cpuPercent).max().orElseThrow(),
                samples.stream().mapToDouble(PerformanceSample::ramUsedGb).average().orElseThrow(),
                samples.stream().mapToDouble(PerformanceSample::ramAvailableGb).average().orElseThrow(),
                samples.stream().mapToLong(PerformanceSample::processCount).average().orElseThrow(),
                powerPlan, samples.size());
    }
}
