package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.game.SystemOperationGuard;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.performance.PerformanceComparison;
import cl.oxman.oxmangameoptimizer.performance.PerformanceLabService;
import cl.oxman.oxmangameoptimizer.performance.PerformanceLabState;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;
import cl.oxman.oxmangameoptimizer.performance.BenchmarkSystemSampler;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public final class BenchmarkSessionCoordinator implements AutoCloseable {
    public static final Duration DEFAULT_CAPTURE = Duration.ofSeconds(60);
    public static final Duration DEFAULT_STABILIZATION = Duration.ofSeconds(5);
    private static final Set<Long> ALLOWED_DURATIONS = Set.of(30L, 60L, 120L);
    private static final long MINIMUM_FRAMES = 100;
    private final BenchmarkSystemSampler lab;
    private final GamePerformanceCapture capture;
    private final BenchmarkBoostService boost;
    private final BenchmarkPersistence persistence;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService worker;
    private final BiConsumer<PerformanceLabState, String> observer;
    private final Duration stabilization;
    private final long minimumFrames;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean boostApplied = new AtomicBoolean();
    private final AtomicReference<PerformanceLabState> state = new AtomicReference<>(PerformanceLabState.IDLE);
    private volatile CompletableFuture<BenchmarkOutcome> running;

    public BenchmarkSessionCoordinator(PerformanceLabService lab, GamePerformanceCapture capture,
            BenchmarkBoostService boost, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer) {
        this(lab, capture, boost, persistence, observer, DEFAULT_STABILIZATION, MINIMUM_FRAMES);
    }

    public BenchmarkSessionCoordinator(BenchmarkSystemSampler lab, GamePerformanceCapture capture,
            BenchmarkBoostService boost, BenchmarkPersistence persistence,
            BiConsumer<PerformanceLabState, String> observer, Duration stabilization, long minimumFrames) {
        this.lab = lab; this.capture = capture; this.boost = boost; this.persistence = persistence; this.observer = observer;
        this.stabilization = stabilization; this.minimumFrames = minimumFrames;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "benchmark-scheduler"));
        worker = Executors.newSingleThreadExecutor(r -> daemon(r, "benchmark-capture"));
    }

    public CompletableFuture<BenchmarkOutcome> start(GameProfile game, Duration duration) {
        return start(new BenchmarkTarget(game.toString(), game::findRunningProcessName, game::isRunning), duration);
    }

    public CompletableFuture<BenchmarkOutcome> start(BenchmarkTarget game, Duration duration) {
        if (!ALLOWED_DURATIONS.contains(duration.toSeconds()) || duration.toMillis() != duration.toSeconds() * 1000)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Capture duration must be 30, 60 or 120 seconds"));
        if (!capture.isAvailable()) return CompletableFuture.failedFuture(new IllegalStateException("PresentMon is not available"));
        if (!active.compareAndSet(false, true))
            return CompletableFuture.failedFuture(new IllegalStateException("Another system operation is active"));
        if (!SystemOperationGuard.acquire(SystemOperationGuard.Operation.BENCHMARK)) {
            active.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Another system operation is active"));
        }
        cancelled.set(false); boostApplied.set(false);
        transition(PerformanceLabState.WAITING_FOR_GAME, "Esperando proceso de " + game.gameName());
        var monitor = scheduler.scheduleAtFixedRate(() -> {
            PerformanceLabState current = state.get();
            if ((current == PerformanceLabState.CAPTURING || current == PerformanceLabState.CAPTURING_OPTIMIZED)
                    && !game.running().getAsBoolean()) cancel("game process ended");
        }, 1, 1, TimeUnit.SECONDS);

        running = waitForProcess(game, Duration.ofMinutes(2))
                .thenCompose(process -> capturePair(game.gameName(), process, duration, false))
                .thenCompose(before -> {
                    checkCancelled(); validate(before.game());
                    transition(PerformanceLabState.APPLYING_BOOST, "Aplicando Competitive Mode");
                    boostApplied.set(true);
                    return boost.apply(game.gameName()).thenApply(report -> {
                        if (report.hasCriticalFailure()) throw new CompletionException(new IllegalStateException("Critical boost failure"));
                        observer.accept(state.get(), "Boost: " + report.applied() + " applied, " + report.omitted() + " omitted, " + report.failed() + " failed");
                        return new Before(before, report);
                    });
                })
                .thenCompose(before -> {
                    checkCancelled(); transition(PerformanceLabState.STABILIZING, "Estabilizando durante 5 segundos");
                    return delay(stabilization).thenApply(ignored -> before);
                })
                .thenCompose(before -> capturePair(game.gameName(), before.capture().targetProcess(), duration, true)
                        .thenApply(after -> new Both(before, after)))
                .thenApply(both -> analyzeAndSave(game, duration, both))
                .thenCompose(outcome -> boost.restore().thenApply(report -> {
                    if (!report.fullyRestored()) throw new CompletionException(new IllegalStateException("Windows restoration failed"));
                    boostApplied.set(false);
                    boolean saved = true;
                    try { persistence.save(outcome.record()); }
                    catch (Exception exception) { saved = false; observer.accept(state.get(), "Resultado medido; no se pudo guardar JSON: " + exception.getMessage()); }
                    return new BenchmarkOutcome(outcome.record(), outcome.gaming(), outcome.system(), outcome.interpretation(), saved);
                }))
                .handle((outcome, error) -> {
                    if (error == null) return CompletableFuture.completedFuture(outcome);
                    return cleanupAfterFailure(error).<BenchmarkOutcome>thenCompose(ignored -> CompletableFuture.failedFuture(unwrap(error)));
                }).thenCompose(future -> future)
                .whenComplete((outcome, error) -> {
                    monitor.cancel(true); capture.close(); active.set(false);
                    SystemOperationGuard.release(SystemOperationGuard.Operation.BENCHMARK);
                    transition(error == null ? PerformanceLabState.COMPLETED : PerformanceLabState.FAILED,
                            error == null ? outcome.interpretation().message() : "Benchmark failed: " + unwrap(error).getMessage());
                });
        return running;
    }

    public void cancel(String reason) {
        if (!active.get() || !cancelled.compareAndSet(false, true)) return;
        observer.accept(state.get(), "Benchmark cancelled: " + reason);
        capture.close();
    }

    public PerformanceLabState state() { return state.get(); }

    private CompletableFuture<String> waitForProcess(BenchmarkTarget game, Duration timeout) {
        CompletableFuture<String> result = new CompletableFuture<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        Runnable poll = new Runnable() { public void run() {
            if (cancelled.get()) { result.completeExceptionally(new CancellationException("cancelled")); return; }
            game.findProcessName().ifPresentOrElse(result::complete, () -> {
                if (System.nanoTime() >= deadline) result.completeExceptionally(new IllegalStateException("game process not identified"));
                else scheduler.schedule(this, 500, TimeUnit.MILLISECONDS);
            });
        }};
        scheduler.execute(poll); return result;
    }

    private CompletableFuture<CapturePair> capturePair(String gameName, String process, Duration duration, boolean optimized) {
        checkCancelled();
        observer.accept(state.get(), "Juego: " + gameName);
        observer.accept(state.get(), "Proceso objetivo: " + process);
        observer.accept(state.get(), "PresentMon: iniciando captura " + (optimized ? "BOOST" : "BEFORE"));
        observer.accept(state.get(), "Duración: " + duration.toSeconds() + " segundos");
        transition(optimized ? PerformanceLabState.CAPTURING_OPTIMIZED : PerformanceLabState.CAPTURING,
                "Capturando rendimiento " + (optimized ? "BOOST" : "BEFORE") + "...");
        CompletableFuture<PerformanceSnapshot> system = lab.sampleSystem();
        CompletableFuture<GamePerformanceResult> game = CompletableFuture.supplyAsync(() -> {
            try { capture.start(process, duration); return capture.stop(); }
            catch (Exception exception) { throw new CompletionException(exception); }
        }, worker);
        var progress = scheduler.scheduleAtFixedRate(new Runnable() {
            private int elapsed;
            public void run() { observer.accept(state.get(), "Capturando " + (optimized ? "BOOST" : "BEFORE")
                    + ": " + Math.min(++elapsed, duration.toSeconds()) + "s / " + duration.toSeconds() + "s"); }
        }, 1, 1, TimeUnit.SECONDS);
        CompletableFuture<CapturePair> combined = new CompletableFuture<>();
        game.whenComplete((gameResult, gameError) -> {
            if (gameError != null) {
                combined.completeExceptionally(unwrap(gameError));
                system.cancel(true);
            } else system.whenComplete((systemResult, systemError) -> {
                if (systemError != null) combined.completeExceptionally(unwrap(systemError));
                else combined.complete(new CapturePair(gameResult, systemResult, process));
            });
        });
        system.whenComplete((systemResult, systemError) -> {
            if (systemError != null && !combined.isDone()) {
                capture.close();
                combined.completeExceptionally(unwrap(systemError));
            }
        });
        return combined
                .whenComplete((result, error) -> progress.cancel(true));
    }

    private BenchmarkOutcome analyzeAndSave(BenchmarkTarget game, Duration duration, Both both) {
        checkCancelled(); validate(both.after().game()); transition(PerformanceLabState.ANALYZING, "Analizando resultados");
        GamePerformanceComparison gaming = new GamePerformanceComparison(both.before().capture().game(), both.after().game());
        PerformanceComparison system = new PerformanceComparison(both.before().capture().system(), both.after().system());
        OptimizationReport report = both.before().report();
        PerformanceInterpretation interpretation = PerformanceInterpretation.from(gaming, report.applied());
        BenchmarkRecord record = new BenchmarkRecord("BASELINE_VS_SAFE_BOOST", game.gameName(),
                both.after().game().processName(), Instant.now(), duration, both.before().capture().game(), both.after().game(),
                both.before().capture().system(), both.after().system(), report, report.applied(), "1.0", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        return new BenchmarkOutcome(record, gaming, system, interpretation, false);
    }

    private CompletableFuture<Void> cleanupAfterFailure(Throwable error) {
        capture.close();
        return boostApplied.getAndSet(false) ? boost.restore().thenAccept(report -> {
            if (!report.fullyRestored()) throw new CompletionException(new IllegalStateException("Windows restoration failed", unwrap(error)));
        }) : CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> delay(Duration duration) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        scheduler.schedule(() -> { try { checkCancelled(); result.complete(null); } catch (Throwable error) { result.completeExceptionally(error); } }, duration.toMillis(), TimeUnit.MILLISECONDS);
        return result;
    }
    private void validate(GamePerformanceResult result) { if (result.frameCount() < minimumFrames) throw new CompletionException(new IllegalStateException("Capture has only " + result.frameCount() + " frames")); }
    private void checkCancelled() { if (cancelled.get()) throw new CancellationException("benchmark cancelled"); }
    private void transition(PerformanceLabState next, String message) { state.set(next); observer.accept(next, message); }
    private static Throwable unwrap(Throwable error) { while ((error instanceof CompletionException) && error.getCause() != null) error = error.getCause(); return error; }
    private static Thread daemon(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }
    public void close() {
        cancel("application closing"); capture.close();
        CompletableFuture<BenchmarkOutcome> future = running;
        if (future != null && !future.isDone()) try { future.orTimeout(15, TimeUnit.SECONDS).join(); } catch (RuntimeException ignored) { }
        scheduler.shutdownNow(); worker.shutdownNow();
        if (lab instanceof AutoCloseable closeable) try { closeable.close(); } catch (Exception ignored) { }
    }
    private record CapturePair(GamePerformanceResult game, PerformanceSnapshot system, String targetProcess) { }
    private record Before(CapturePair capture, OptimizationReport report) { }
    private record Both(Before before, CapturePair after) { }
}
