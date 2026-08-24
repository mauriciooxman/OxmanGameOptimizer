package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.game.SystemOperationGuard;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.performance.BenchmarkSystemSampler;
import cl.oxman.oxmangameoptimizer.performance.PerformanceLabState;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.Optional;

/** Reproducible AB/BA experiment runner. Each capture is applied and fully restored in isolation. */
public final class ExperimentalBenchmarkCoordinator implements AutoCloseable {
    public static final int DEFAULT_RUNS = 3;
    private static final Set<Integer> ALLOWED_RUNS = Set.of(1, 3, 5);
    private static final Set<Long> ALLOWED_DURATIONS = Set.of(30L, 60L, 120L);
    private final BenchmarkSystemSampler sampler;
    private final GamePerformanceCapture capture;
    private final ExperimentalConfigurationService configurations;
    private final BenchmarkPersistence persistence;
    private final BiConsumer<PerformanceLabState, String> observer;
    private final Duration stabilization;
    private final long minimumFrames;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "priority-experiment"); t.setDaemon(true); return t; });
    private final AtomicBoolean active = new AtomicBoolean(), cancelled = new AtomicBoolean(), configurationActive = new AtomicBoolean();

    public ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer) {
        this(sampler, capture, configurations, persistence, observer, Duration.ofSeconds(5), 100);
    }
    ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer, Duration stabilization, long minimumFrames) {
        this.sampler = sampler; this.capture = capture; this.configurations = configurations;
        this.persistence = persistence; this.observer = observer; this.stabilization = stabilization; this.minimumFrames = minimumFrames;
    }

    public CompletableFuture<ExperimentResult> start(GameProfile game, Duration duration, int runs) {
        return start(new Target(game, game::findRunningProcessName), duration, runs);
    }
    CompletableFuture<ExperimentResult> start(GameProfile game, Supplier<Optional<String>> process, Duration duration, int runs) {
        return start(new Target(game, process), duration, runs);
    }
    private CompletableFuture<ExperimentResult> start(Target target, Duration duration, int runs) {
        if (!ALLOWED_RUNS.contains(runs)) return CompletableFuture.failedFuture(new IllegalArgumentException("Runs must be 1, 3 or 5"));
        if (!ALLOWED_DURATIONS.contains(duration.toSeconds())) return CompletableFuture.failedFuture(new IllegalArgumentException("Duration must be 30, 60 or 120 seconds"));
        if (!capture.isAvailable()) return CompletableFuture.failedFuture(new IllegalStateException("PresentMon is not available"));
        if (!active.compareAndSet(false, true) || !SystemOperationGuard.acquire(SystemOperationGuard.Operation.BENCHMARK)) {
            active.set(false); return CompletableFuture.failedFuture(new IllegalStateException("Another system operation is active"));
        }
        cancelled.set(false);
        return CompletableFuture.supplyAsync(() -> execute(target, duration, runs), worker).whenComplete((result, error) -> {
            capture.close(); active.set(false); SystemOperationGuard.release(SystemOperationGuard.Operation.BENCHMARK);
            observer.accept(error == null ? PerformanceLabState.COMPLETED : PerformanceLabState.FAILED,
                    error == null ? result.interpretation().message() : "Experimento cancelado o fallido");
        });
    }

    private ExperimentResult execute(Target target, Duration duration, int runCount) {
        List<CompletedRun> completed = new ArrayList<>();
        try {
            for (int number = 1; number <= runCount; number++) {
                checkCancelled();
                ExperimentOrder order = ExperimentOrder.forRun(number);
                observer.accept(PerformanceLabState.APPLYING_BOOST, "Experiment: Process Priority · Run " + number + "/" + runCount + " · " + order);
                Captured first = captureConfiguration(target, duration, order.first());
                Captured second = captureConfiguration(target, duration, order.second());
                Captured safe = order.first() == ExperimentConfiguration.SAFE ? first : second;
                Captured priority = order.first() == ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL ? first : second;
                completed.add(new CompletedRun(new ExperimentRun(number, order, safe.game(), priority.game()), safe, priority));
            }
            ExperimentResult result = ExperimentResult.analyze(completed.stream().map(CompletedRun::run).toList());
            checkCancelled();
            for (CompletedRun value : completed) persistence.save(record(target.game(), duration, value));
            return result;
        } catch (RuntimeException exception) {
            restoreQuietly();
            throw exception;
        } catch (Exception exception) {
            restoreQuietly();
            throw new IllegalStateException(exception);
        }
    }

    private Captured captureConfiguration(Target target, Duration duration, ExperimentConfiguration configuration) {
        checkCancelled();
        observer.accept(PerformanceLabState.APPLYING_BOOST, "Configuration: " + configuration.label());
        configurationActive.set(true);
        OptimizationReport report = configurations.apply(target.game(), configuration);
        try {
            stabilize(); checkCancelled();
            String process = target.process().get().orElseThrow(() -> new IllegalStateException("El proceso del juego terminó"));
            observer.accept(configuration.usesPriority() ? PerformanceLabState.CAPTURING_OPTIMIZED : PerformanceLabState.CAPTURING,
                    "Capturando " + configuration.label() + " durante " + duration.toSeconds() + " segundos");
            CompletableFuture<PerformanceSnapshot> system = sampler.sampleSystem();
            capture.start(process, duration);
            GamePerformanceResult result = capture.stop();
            if (result.frameCount() < minimumFrames) throw new IllegalStateException("Capture has only " + result.frameCount() + " frames");
            return new Captured(result, system.join(), report);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception);
        } finally {
            OptimizationReport restored = configurations.restore(); configurationActive.set(false);
            if (!restored.fullyRestored()) throw new IllegalStateException("Windows restoration failed");
            stabilize();
        }
    }

    private BenchmarkRecord record(GameProfile game, Duration duration, CompletedRun value) {
        int active = value.priority().report().applied();
        return new BenchmarkRecord("SAFE_BOOST_VS_PRIORITY", game.toString(), value.run().safe().processName(), Instant.now(), duration,
                value.run().safe(), value.run().aboveNormal(), value.safe().system(), value.priority().system(), value.priority().report(), active,
                "1.0", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                ExperimentConfiguration.SAFE.name(), ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL.name(), "PROCESS_PRIORITY",
                value.run().runNumber(), value.run().order().toString(), List.of("PROCESS_PRIORITY_ABOVE_NORMAL"));
    }
    private void stabilize() {
        if (stabilization.isZero()) return;
        try { Thread.sleep(stabilization); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CancellationException(); }
    }
    private void checkCancelled() { if (cancelled.get()) throw new CancellationException("Experiment cancelled"); }
    private void restoreQuietly() { if (configurationActive.getAndSet(false)) try { configurations.restore(); } catch (RuntimeException ignored) { } }
    public void cancel(String reason) { if (active.get()) { cancelled.set(true); capture.close(); observer.accept(PerformanceLabState.FAILED, "Experiment cancelled: " + reason); } }
    @Override public void close() { cancel("application closing"); worker.shutdownNow(); capture.close(); }
    private record Captured(GamePerformanceResult game, PerformanceSnapshot system, OptimizationReport report) { }
    private record CompletedRun(ExperimentRun run, Captured safe, Captured priority) { }
    private record Target(GameProfile game, Supplier<Optional<String>> process) { }
}
