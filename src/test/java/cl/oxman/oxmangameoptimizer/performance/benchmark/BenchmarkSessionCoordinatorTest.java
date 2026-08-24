package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.performance.PerformanceLabState;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSample;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSessionCoordinatorTest {
    private BenchmarkSessionCoordinator coordinator;
    @AfterEach void close() { if (coordinator != null) coordinator.close(); }

    @Test void happyPathCapturesBoostsComparesSavesAndRestores() {
        FakeCapture capture = new FakeCapture(result(120, 10), result(120, 8));
        FakeBoost boost = new FakeBoost(); AtomicInteger saved = new AtomicInteger(); List<PerformanceLabState> states = new ArrayList<>();
        coordinator = create(capture, boost, record -> saved.incrementAndGet(), states);
        BenchmarkOutcome outcome = coordinator.start(target(), Duration.ofSeconds(30)).join();
        assertEquals(1, boost.applies.get()); assertEquals(1, boost.restores.get()); assertEquals(1, saved.get());
        assertEquals(PerformanceInterpretation.MEASURABLE_IMPROVEMENT, outcome.interpretation());
        assertTrue(states.containsAll(List.of(PerformanceLabState.CAPTURING, PerformanceLabState.APPLYING_BOOST,
                PerformanceLabState.STABILIZING, PerformanceLabState.CAPTURING_OPTIMIZED, PerformanceLabState.ANALYZING,
                PerformanceLabState.COMPLETED)));
    }

    @Test void zeroAppliedNeverClaimsImprovementOrRegression() {
        GamePerformanceComparison comparison = new GamePerformanceComparison(result(120, 10), result(120, 5));
        assertEquals(PerformanceInterpretation.NO_OPTIMIZATIONS_APPLIED,
                PerformanceInterpretation.from(comparison, 0));
    }

    @Test void targetResolvesCs2AndGenericProcessNamesWithoutLeakingLambdaInLogs() {
        BenchmarkTarget cs2 = new BenchmarkTarget("Counter-Strike 2", () -> Optional.of("cs2.exe"), () -> true);
        BenchmarkTarget generic = new BenchmarkTarget("Example", () -> Optional.of("example.exe"), () -> true);
        assertEquals("cs2.exe", cs2.findProcessName().orElseThrow());
        assertEquals("example.exe", generic.findProcessName().orElseThrow());
        assertEquals("Counter-Strike 2", cs2.toString());
        assertFalse(cs2.toString().contains("$$Lambda"));
    }

    @Test void coordinatorPassesExactProcessAndSelectedDurationToCapture() {
        FakeCapture capture = new FakeCapture(result(120, 10), result(120, 8));
        FakeBoost boost = new FakeBoost(); List<String> messages = new ArrayList<>();
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        coordinator = new BenchmarkSessionCoordinator(() -> CompletableFuture.completedFuture(snapshot), capture, boost,
                record -> { }, (state, message) -> messages.add(message), Duration.ZERO, 100);
        coordinator.start(new BenchmarkTarget("Counter-Strike 2", () -> Optional.of("cs2.exe"), () -> true),
                Duration.ofSeconds(30)).join();
        assertEquals(List.of("cs2.exe", "cs2.exe"), capture.processes);
        assertEquals(List.of(Duration.ofSeconds(30), Duration.ofSeconds(30)), capture.durations);
        assertTrue(messages.contains("Juego: Counter-Strike 2"));
        assertTrue(messages.contains("Proceso objetivo: cs2.exe"));
        assertTrue(messages.contains("PresentMon: iniciando captura BEFORE"));
        assertTrue(messages.contains("Duración: 30 segundos"));
    }

    @Test void invalidBeforeDoesNotApplyBoostOrSave() {
        FakeCapture capture = new FakeCapture(result(5, 10)); FakeBoost boost = new FakeBoost(); AtomicInteger saved = new AtomicInteger();
        coordinator = create(capture, boost, record -> saved.incrementAndGet(), new ArrayList<>());
        assertThrows(CompletionException.class, () -> coordinator.start(target(), Duration.ofSeconds(30)).join());
        assertEquals(0, boost.applies.get()); assertEquals(0, saved.get());
    }

    @Test void boostFailureTriggersRestoreAndSkipsAfter() {
        FakeCapture capture = new FakeCapture(result(120, 10)); FakeBoost boost = new FakeBoost();
        boost.applyFailure = new IllegalStateException("boost failed");
        coordinator = create(capture, boost, record -> fail(), new ArrayList<>());
        assertThrows(CompletionException.class, () -> coordinator.start(target(), Duration.ofSeconds(30)).join());
        assertEquals(1, boost.restores.get()); assertEquals(1, capture.stops.get());
    }

    @Test void afterFailureTriggersRestoreAndDoesNotPersist() {
        FakeCapture capture = new FakeCapture(result(120, 10), new IllegalStateException("capture failed"));
        FakeBoost boost = new FakeBoost(); AtomicInteger saved = new AtomicInteger();
        coordinator = create(capture, boost, record -> saved.incrementAndGet(), new ArrayList<>());
        assertThrows(CompletionException.class, () -> coordinator.start(target(), Duration.ofSeconds(30)).join());
        assertEquals(1, boost.restores.get()); assertEquals(0, saved.get());
    }

    @Test void cancelBeforeBoostDoesNotRestoreOrSave() throws Exception {
        FakeCapture capture = new FakeCapture(result(120, 10)); FakeBoost boost = new FakeBoost();
        BenchmarkTarget waiting = new BenchmarkTarget("Game", Optional::<String>empty, () -> true);
        coordinator = create(capture, boost, record -> fail(), new ArrayList<>());
        var future = coordinator.start(waiting, Duration.ofSeconds(30)); coordinator.cancel("test");
        assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS));
        assertEquals(0, boost.applies.get()); assertEquals(0, boost.restores.get());
    }

    @Test void cancelAfterBoostRestoresAndResolvesFuture() throws Exception {
        BlockingSecondCapture capture = new BlockingSecondCapture(); FakeBoost boost = new FakeBoost();
        coordinator = create(capture, boost, record -> fail(), new ArrayList<>());
        var future = coordinator.start(target(), Duration.ofSeconds(30));
        assertTrue(capture.secondStarted.await(2, TimeUnit.SECONDS)); coordinator.cancel("test");
        assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS)); assertEquals(1, boost.restores.get());
    }

    @Test void restoreFailureMarksBenchmarkFailedAndDoesNotPersist() {
        FakeCapture capture = new FakeCapture(result(120, 10), result(120, 8)); FakeBoost boost = new FakeBoost();
        boost.restoreSuccess = false; AtomicInteger saved = new AtomicInteger();
        coordinator = create(capture, boost, record -> saved.incrementAndGet(), new ArrayList<>());
        assertThrows(CompletionException.class, () -> coordinator.start(target(), Duration.ofSeconds(30)).join());
        assertEquals(0, saved.get()); assertEquals(PerformanceLabState.FAILED, coordinator.state());
    }

    @Test void gameCloseDuringCaptureCancelsWithoutApplyingBoost() throws Exception {
        BlockingSecondCapture capture = new BlockingSecondCapture();
        // Block the first capture instead of the second.
        capture.count.set(1);
        FakeBoost boost = new FakeBoost(); AtomicBoolean running = new AtomicBoolean(true);
        BenchmarkTarget target = new BenchmarkTarget("Game", () -> Optional.of("game.exe"), running::get);
        coordinator = create(capture, boost, record -> fail(), new ArrayList<>());
        var future = coordinator.start(target, Duration.ofSeconds(30));
        assertTrue(capture.secondStarted.await(2, TimeUnit.SECONDS)); running.set(false);
        assertThrows(Exception.class, () -> future.get(3, TimeUnit.SECONDS)); assertEquals(0, boost.applies.get());
    }

    @Test void completedShortSystemSampleDoesNotCancelLongGameCapture() throws Exception {
        BlockingFirstCapture capture = new BlockingFirstCapture(); FakeBoost boost = new FakeBoost();
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        coordinator = new BenchmarkSessionCoordinator(() -> CompletableFuture.completedFuture(snapshot), capture, boost,
                record -> fail(), (state, message) -> { }, Duration.ZERO, 100);
        CompletableFuture<BenchmarkOutcome> future = coordinator.start(target(), Duration.ofSeconds(60));
        assertTrue(capture.started.await(2, TimeUnit.SECONDS));
        Thread.sleep(150);
        assertFalse(future.isDone(), "finishing the system sampler must not complete or cancel PresentMon");
        assertFalse(capture.closed.get());
        coordinator.cancel("test complete");
        assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS));
    }

    @Test void immediatePresentMonStartFailureDoesNotWaitForSystemSampler() throws Exception {
        CompletableFuture<PerformanceSnapshot> sampling = new CompletableFuture<>();
        GamePerformanceCapture capture = new GamePerformanceCapture() {
            public boolean isAvailable() { return true; }
            public void start(String process, Duration duration) { throw new IllegalStateException("trace start failed"); }
            public GamePerformanceResult stop() { throw new AssertionError("stop must not run after start failure"); }
            public void close() { }
        };
        FakeBoost boost = new FakeBoost();
        coordinator = new BenchmarkSessionCoordinator(() -> sampling, capture, boost, record -> fail(),
                (state, message) -> { }, Duration.ZERO, 100);

        CompletableFuture<BenchmarkOutcome> future = coordinator.start(target(), Duration.ofSeconds(30));

        Exception error = assertThrows(Exception.class, () -> future.get(2, TimeUnit.SECONDS));
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        assertEquals("trace start failed", cause.getMessage());
        assertTrue(sampling.isCancelled());
        assertEquals(0, boost.applies.get());
    }

    private BenchmarkSessionCoordinator create(GamePerformanceCapture capture, FakeBoost boost, BenchmarkPersistence store, List<PerformanceLabState> states) {
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        return new BenchmarkSessionCoordinator(() -> CompletableFuture.completedFuture(snapshot), capture, boost, store,
                (state, message) -> states.add(state), Duration.ZERO, 100);
    }
    private static BenchmarkTarget target() { return new BenchmarkTarget("Test Game", () -> Optional.of("game.exe"), () -> true); }
    private static GamePerformanceResult result(long frames, double frameMs) {
        return new GamePerformanceResult(OptionalDouble.of(1000 / frameMs), OptionalDouble.of(900 / frameMs),
                OptionalDouble.of(frameMs), OptionalDouble.empty(), OptionalDouble.empty(), frames,
                Duration.ofSeconds(30), "game.exe", Instant.now());
    }
    private static final class FakeBoost implements BenchmarkBoostService {
        AtomicInteger applies = new AtomicInteger(), restores = new AtomicInteger(); RuntimeException applyFailure; boolean restoreSuccess = true;
        public CompletableFuture<OptimizationReport> apply(String game) { applies.incrementAndGet(); return applyFailure == null
                ? CompletableFuture.completedFuture(new OptimizationReport(2, 2, false)) : CompletableFuture.failedFuture(applyFailure); }
        public CompletableFuture<OptimizationReport> restore() { restores.incrementAndGet(); return CompletableFuture.completedFuture(new OptimizationReport(2, 2, restoreSuccess)); }
    }
    private static final class FakeCapture implements GamePerformanceCapture {
        Queue<Object> results = new ArrayDeque<>(); AtomicInteger stops = new AtomicInteger();
        List<String> processes = new ArrayList<>(); List<Duration> durations = new ArrayList<>();
        FakeCapture(Object... values) { results.addAll(List.of(values)); }
        public boolean isAvailable() { return true; } public void start(String process, Duration duration) {
            processes.add(process); durations.add(duration);
        }
        public GamePerformanceResult stop() { stops.incrementAndGet(); Object value = results.remove(); if (value instanceof RuntimeException error) throw error; return (GamePerformanceResult) value; }
        public void close() { }
    }
    private static final class BlockingSecondCapture implements GamePerformanceCapture {
        AtomicInteger count = new AtomicInteger(); CountDownLatch secondStarted = new CountDownLatch(1), release = new CountDownLatch(1);
        public boolean isAvailable() { return true; } public void start(String process, Duration duration) { }
        public GamePerformanceResult stop() {
            if (count.incrementAndGet() == 1) return result(120, 10);
            secondStarted.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new IllegalStateException("closed");
        }
        public void close() { release.countDown(); }
    }
    private static final class BlockingFirstCapture implements GamePerformanceCapture {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        public boolean isAvailable() { return true; }
        public void start(String process, Duration duration) { started.countDown(); }
        public GamePerformanceResult stop() {
            try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            throw new IllegalStateException("closed");
        }
        public void close() { closed.set(true); release.countDown(); }
    }
}
