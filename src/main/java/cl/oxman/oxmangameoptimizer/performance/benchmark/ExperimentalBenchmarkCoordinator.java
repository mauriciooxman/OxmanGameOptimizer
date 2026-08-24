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
    private final ExperimentConfiguration experimentalConfiguration;
    private final ExecutorService worker;
    private final AtomicBoolean active = new AtomicBoolean(), cancelled = new AtomicBoolean(), configurationActive = new AtomicBoolean();

    public ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer) {
        this(sampler, capture, configurations, persistence, observer,
                ExperimentConfiguration.SAFE_PLUS_HIGH_QOS, Duration.ofSeconds(5), 100);
    }
    public ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer, ExperimentConfiguration experimentalConfiguration) {
        this(sampler, capture, configurations, persistence, observer, experimentalConfiguration, Duration.ofSeconds(5), 100);
    }
    ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer, Duration stabilization, long minimumFrames) {
        this(sampler, capture, configurations, persistence, observer,
                ExperimentConfiguration.SAFE_PLUS_HIGH_QOS, stabilization, minimumFrames);
    }
    ExperimentalBenchmarkCoordinator(BenchmarkSystemSampler sampler, GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer, ExperimentConfiguration experimentalConfiguration,
            Duration stabilization, long minimumFrames) {
        if (experimentalConfiguration == null || !experimentalConfiguration.isExperimental())
            throw new IllegalArgumentException("An experimental configuration is required");
        this.sampler = sampler; this.capture = capture; this.configurations = configurations;
        this.persistence = persistence; this.observer = observer; this.stabilization = stabilization; this.minimumFrames = minimumFrames;
        this.experimentalConfiguration = experimentalConfiguration;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            String threadName = experimentalConfiguration.usesBackgroundEcoQos() ? "background-ecoqos-experiment"
                    : experimentalConfiguration.usesHighQos() ? "high-qos-experiment" : "priority-experiment";
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
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
            observer.accept(error == null && result.interpretation() == ExperimentInterpretation.NO_CHANGE
                            ? PerformanceLabState.NO_CHANGE
                            : error == null ? PerformanceLabState.COMPLETED : PerformanceLabState.FAILED,
                    error == null ? result.interpretation().message() : "Experimento cancelado o fallido");
        });
    }

    private ExperimentResult execute(Target target, Duration duration, int runCount) {
        List<CompletedRun> completed = new ArrayList<>();
        try {
            for (int number = 1; number <= runCount; number++) {
                checkCancelled();
                ExperimentOrder order = ExperimentOrder.forRun(number, experimentalConfiguration);
                observer.accept(PerformanceLabState.APPLYING_BOOST, "Experiment: " + experimentName()
                        + " · Run " + number + "/" + runCount + " · " + order);
                Captured first = captureConfiguration(target, duration, order.first());
                if (first.validity() == ConfigurationValidity.NO_CHANGE)
                    return finishNoChange(target, duration, completed, number, order, first, null);
                Captured second = captureConfiguration(target, duration, order.second());
                if (second.validity() == ConfigurationValidity.NO_CHANGE)
                    return finishNoChange(target, duration, completed, number, order, second, first);
                Captured safe = order.first() == ExperimentConfiguration.SAFE ? first : second;
                Captured experimental = order.first() == experimentalConfiguration ? first : second;
                completed.add(new CompletedRun(new ExperimentRun(number, order, safe.game(), experimental.game(), experimental.validity()), safe, experimental));
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
        ConfigurationApplication application = configurations.apply(target.game(), configuration);
        OptimizationReport report = application.report();
        try {
            if (application.validity() == ConfigurationValidity.NO_CHANGE) {
                observer.accept(PerformanceLabState.NO_CHANGE, noChangeMessage());
                return new Captured(null, null, report, ConfigurationValidity.NO_CHANGE);
            }
            stabilize(); checkCancelled();
            String process = target.process().get().orElseThrow(() -> new IllegalStateException("El proceso del juego terminó"));
            observer.accept(configuration.isExperimental() ? PerformanceLabState.CAPTURING_OPTIMIZED : PerformanceLabState.CAPTURING,
                    "Capturando " + configuration.label() + " durante " + duration.toSeconds() + " segundos");
            CompletableFuture<PerformanceSnapshot> system = sampler.sampleSystem();
            PriorityCaptureMonitor monitor = configuration.isExperimental()
                    ? configurations.monitorExperimentalConfigurationDuringCapture() : PriorityCaptureMonitor.STABLE;
            GamePerformanceResult result;
            try (monitor) {
                capture.start(process, duration);
                result = capture.stop();
            }
            if (result.frameCount() < minimumFrames) throw new IllegalStateException("Capture has only " + result.frameCount() + " frames");
            ConfigurationValidity validity = monitor.drifted()
                    ? ConfigurationValidity.CONFIGURATION_DRIFT : ConfigurationValidity.VALID;
            if (validity == ConfigurationValidity.CONFIGURATION_DRIFT)
                observer.accept(PerformanceLabState.FAILED,
                        experimentName() + " fue modificado externamente durante la prueba. El resultado no puede utilizarse.");
            return new Captured(result, system.join(), report, validity);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime : new IllegalStateException(exception);
        } finally {
            OptimizationReport restored = configurations.restore(); configurationActive.set(false);
            if (!restored.fullyRestored()) throw new IllegalStateException("Windows restoration failed");
            stabilize();
        }
    }

    private ExperimentResult finishNoChange(Target target, Duration duration, List<CompletedRun> completed,
            int number, ExperimentOrder order, Captured noChange, Captured other) throws Exception {
        GamePerformanceResult reference = other != null && other.game() != null ? other.game()
                : completed.isEmpty() ? emptyResult(target) : completed.getLast().run().safe();
        Captured safe = other != null && other.game() != null ? other
                : new Captured(reference, other == null ? null : other.system(), noChange.report(), ConfigurationValidity.NO_CHANGE);
        ExperimentRun marker = new ExperimentRun(number, order, reference, reference, ConfigurationValidity.NO_CHANGE);
        CompletedRun terminal = new CompletedRun(marker, safe, noChange);
        persistence.save(record(target.game(), duration, terminal));
        return ExperimentResult.analyze(List.of(marker));
    }

    private static GamePerformanceResult emptyResult(Target target) {
        return new GamePerformanceResult(java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(),
                java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(),
                0, Duration.ZERO, target.process().get().orElse("unknown"), Instant.now());
    }

    private BenchmarkRecord record(GameProfile game, Duration duration, CompletedRun value) {
        int active = value.priority().report().applied();
        String benchmarkType = experimentalConfiguration.usesBackgroundEcoQos() ? "SAFE_VS_BACKGROUND_ECOQOS"
                : experimentalConfiguration.usesHighQos() ? "SAFE_BOOST_VS_HIGH_QOS" : "SAFE_BOOST_VS_PRIORITY";
        return new BenchmarkRecord(benchmarkType,
                game.toString(), value.run().safe().processName(), Instant.now(), duration,
                value.run().safe(), value.run().aboveNormal(), value.safe().system(), value.priority().system(), value.priority().report(), active,
                "1.0.0", System.getProperty("os.name") + " " + System.getProperty("os.version"),
                ExperimentConfiguration.SAFE.name(), experimentalConfiguration.name(), experimentType(),
                value.run().runNumber(), value.run().order().toString(), List.of(actionId()),
                value.run().validity());
    }
    private String experimentName() {
        return experimentalConfiguration.usesBackgroundEcoQos() ? "Background Load Guard"
                : experimentalConfiguration.usesHighQos() ? "Process Power Throttling" : "Process Priority";
    }
    private String experimentType() {
        return experimentalConfiguration.usesBackgroundEcoQos() ? "BACKGROUND_ECOQOS"
                : experimentalConfiguration.usesHighQos() ? "PROCESS_POWER_THROTTLING" : "PROCESS_PRIORITY";
    }
    private String actionId() {
        return experimentalConfiguration.usesBackgroundEcoQos() ? "BACKGROUND_PROCESS_EXECUTION_SPEED_ENABLED"
                : experimentalConfiguration.usesHighQos() ? "PROCESS_POWER_THROTTLING_EXECUTION_SPEED_DISABLED"
                : "PROCESS_PRIORITY_ABOVE_NORMAL";
    }
    private String noChangeMessage() {
        return experimentalConfiguration.usesBackgroundEcoQos()
                ? "No existen candidatos de fondo seguros que requieran EcoQoS. No se realizó captura B."
                : ExperimentInterpretation.NO_CHANGE.message();
    }
    private void stabilize() {
        if (stabilization.isZero()) return;
        try { Thread.sleep(stabilization); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CancellationException(); }
    }
    private void checkCancelled() { if (cancelled.get()) throw new CancellationException("Experiment cancelled"); }
    private void restoreQuietly() { if (configurationActive.getAndSet(false)) try { configurations.restore(); } catch (RuntimeException ignored) { } }
    public void cancel(String reason) { if (active.get()) { cancelled.set(true); capture.close(); observer.accept(PerformanceLabState.FAILED, "Experiment cancelled: " + reason); } }
    @Override public void close() { cancel("application closing"); worker.shutdownNow(); capture.close(); }
    private record Captured(GamePerformanceResult game, PerformanceSnapshot system, OptimizationReport report,
                            ConfigurationValidity validity) { }
    private record CompletedRun(ExperimentRun run, Captured safe, Captured priority) { }
    private record Target(GameProfile game, Supplier<Optional<String>> process) { }
}
