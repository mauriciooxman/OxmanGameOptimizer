package cl.oxman.oxmangameoptimizer.performance;

import cl.oxman.oxmangameoptimizer.system.SystemCommandRunner;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PerformanceLabService implements AutoCloseable, BenchmarkSystemSampler {
    public static final Duration DEFAULT_SAMPLE_DURATION = Duration.ofSeconds(10);
    public static final Duration DEFAULT_SAMPLE_INTERVAL = Duration.ofMillis(500);
    public static final Duration DEFAULT_STABILIZATION = Duration.ofSeconds(4);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "performance-lab"); t.setDaemon(true); return t; });
    private final SystemBaselineSampler sampler;
    public PerformanceLabService(SystemCommandRunner commands) { sampler = new SystemBaselineSampler(commands, executor); }
    public CompletableFuture<PerformanceSnapshot> sampleSystem() { return sampler.sample(DEFAULT_SAMPLE_DURATION, DEFAULT_SAMPLE_INTERVAL); }
    public CompletableFuture<PerformanceSnapshot> sampleAfterStabilization() {
        return CompletableFuture.runAsync(() -> { try { Thread.sleep(DEFAULT_STABILIZATION.toMillis()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }, executor)
                .thenCompose(ignored -> sampleSystem());
    }
    public void close() { executor.shutdownNow(); }
}
