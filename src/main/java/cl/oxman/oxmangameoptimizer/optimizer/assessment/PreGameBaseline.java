package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.performance.benchmark.BackgroundProcessObservation;
import java.util.List;

public record PreGameBaseline(int sampleCount, double cpuAveragePercent, double memoryUsedPercent,
        long availableMemoryBytes, int processCount, List<BackgroundProcessObservation> processes, boolean complete) {
    public PreGameBaseline { processes = List.copyOf(processes); }
    public static PreGameBaseline unavailable() { return new PreGameBaseline(0, Double.NaN, Double.NaN, -1, -1, List.of(), false); }
    public double backgroundCpu(long gamePid, long oxmanPid) {
        return processes.stream().filter(p -> !p.foreground() && p.identity().pid() != gamePid && p.identity().pid() != oxmanPid)
                .mapToDouble(BackgroundProcessObservation::cpuAveragePercent).sum();
    }
}
