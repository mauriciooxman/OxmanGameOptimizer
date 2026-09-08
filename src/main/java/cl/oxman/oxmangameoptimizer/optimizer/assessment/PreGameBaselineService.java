package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import cl.oxman.oxmangameoptimizer.performance.benchmark.*;
import cl.oxman.oxmangameoptimizer.system.ProcessIdentity;
import java.time.Duration;
import java.util.*;

/** Independent CPU ticks: the dashboard's polling cannot consume the diagnosis samples. */
public final class PreGameBaselineService {
    public record Sample(double cpuPercent, double memoryUsedPercent, long availableMemoryBytes,
            int processCount, List<BackgroundProcessObservation> processes) { }
    @FunctionalInterface public interface Source { Sample sample(Duration interval); }
    private final Source source;
    private final DiagnosisThresholds thresholds;
    public PreGameBaselineService(Source source, DiagnosisThresholds thresholds) { this.source = source; this.thresholds = thresholds; }
    public PreGameBaseline collect() {
        List<Sample> samples = new ArrayList<>();
        try {
            for (int i = 0; i < thresholds.samples(); i++) {
                Sample sample = source.sample(thresholds.interval());
                if (Thread.currentThread().isInterrupted() || !valid(sample)) return PreGameBaseline.unavailable();
                samples.add(sample);
            }
        } catch (RuntimeException exception) { return PreGameBaseline.unavailable(); }
        Map<ProcessIdentity, List<BackgroundProcessObservation>> grouped = new HashMap<>();
        samples.forEach(s -> s.processes().forEach(p -> grouped.computeIfAbsent(p.identity(), key -> new ArrayList<>()).add(p)));
        List<BackgroundProcessObservation> averaged = new ArrayList<>();
        grouped.forEach((identity, values) -> {
            // Require identity and safe context throughout the entire observation window.
            if (values.size() != samples.size()) return;
            var first = values.getFirst();
            averaged.add(new BackgroundProcessObservation(identity, first.user(),
                    values.stream().allMatch(BackgroundProcessObservation::currentUser),
                    values.stream().anyMatch(BackgroundProcessObservation::foreground),
                    values.stream().mapToDouble(BackgroundProcessObservation::cpuAveragePercent).average().orElse(0)));
        });
        return new PreGameBaseline(samples.size(), samples.stream().mapToDouble(Sample::cpuPercent).average().orElseThrow(),
                samples.stream().mapToDouble(Sample::memoryUsedPercent).max().orElseThrow(),
                samples.stream().mapToLong(Sample::availableMemoryBytes).min().orElseThrow(),
                samples.getLast().processCount(), averaged, true);
    }
    private static boolean valid(Sample s) {
        return s != null && percent(s.cpuPercent()) && percent(s.memoryUsedPercent()) && s.availableMemoryBytes() >= 0
                && s.processCount() >= 0 && s.processes() != null && s.processes().stream().allMatch(p ->
                p != null && p.identity() != null && percent(p.cpuAveragePercent()));
    }
    private static boolean percent(double v) { return Double.isFinite(v) && v >= 0 && v <= 100; }
    public static PreGameBaselineService windows(DiagnosisThresholds thresholds) {
        var info = new oshi.SystemInfo();
        var cpu = info.getHardware().getProcessor();
        var memory = info.getHardware().getMemory();
        var processes = new WindowsBackgroundProcessSource();
        return new PreGameBaselineService(interval -> {
            long[] before = cpu.getSystemCpuLoadTicks();
            var observed = processes.observe(interval);
            long total = memory.getTotal(), available = memory.getAvailable();
            return new Sample(cpu.getSystemCpuLoadBetweenTicks(before) * 100,
                    total > 0 ? (total - available) * 100.0 / total : Double.NaN,
                    available, info.getOperatingSystem().getProcessCount(), observed);
        }, thresholds);
    }
}
