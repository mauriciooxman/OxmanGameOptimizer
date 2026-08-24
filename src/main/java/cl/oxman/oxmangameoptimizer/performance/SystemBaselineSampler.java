package cl.oxman.oxmangameoptimizer.performance;

import cl.oxman.oxmangameoptimizer.monitor.HardwareMonitor;
import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public final class SystemBaselineSampler {
    private static final Pattern PLAN = Pattern.compile("\\(([^)]+)\\)");
    private final SystemCommandRunner commands;
    private final Executor executor;
    public SystemBaselineSampler(SystemCommandRunner commands, Executor executor) { this.commands = commands; this.executor = executor; }
    public CompletableFuture<PerformanceSnapshot> sample(Duration duration, Duration interval) {
        if (duration.isNegative() || duration.isZero() || interval.isNegative() || interval.isZero())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Sampling durations must be positive"));
        return CompletableFuture.supplyAsync(() -> {
            List<PerformanceSample> samples = new ArrayList<>();
            long deadline = System.nanoTime() + duration.toNanos();
            do {
                samples.add(new PerformanceSample(HardwareMonitor.getCpuUsage(), HardwareMonitor.getUsedRamGB(),
                        HardwareMonitor.getAvailableRamGB(), ProcessHandle.allProcesses().count()));
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(Math.min(interval.toMillis(), Math.max(1, duration.toMillis()))); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException("Sampling interrupted", exception); }
            } while (System.nanoTime() < deadline);
            String plan = "No disponible";
            var result = commands.run(List.of("powercfg", "/getactivescheme"), Duration.ofSeconds(5));
            if (result.succeeded()) { var matcher = PLAN.matcher(result.output()); if (matcher.find()) plan = matcher.group(1).trim(); }
            return PerformanceSnapshot.from(samples, plan);
        }, executor);
    }
}
