package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.game.GameProfile;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSample;
import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentalBenchmarkCoordinatorTest {
    private ExperimentalBenchmarkCoordinator coordinator;
    @AfterEach void close() { if (coordinator != null) coordinator.close(); }

    @Test void oneRunAppliesAndRestoresEachConfigurationAndPersistsOnlyWhenComplete() {
        FakeConfiguration configuration = new FakeConfiguration(); AtomicInteger saved = new AtomicInteger();
        coordinator = create(new FakeCapture(2), configuration, record -> saved.incrementAndGet());
        ExperimentResult result = coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();
        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL), configuration.applied);
        assertEquals(2, configuration.restores.get()); assertEquals(1, saved.get()); assertEquals(1, result.runs().size());
    }

    @Test void threeRunsAlternateAbBaAndRestoreBetweenAllSixCaptures() {
        FakeConfiguration configuration = new FakeConfiguration(); AtomicInteger saved = new AtomicInteger();
        coordinator = create(new FakeCapture(6), configuration, record -> saved.incrementAndGet());
        ExperimentResult result = coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 3).join();
        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL,
                ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL, ExperimentConfiguration.SAFE,
                ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL), configuration.applied);
        assertEquals(6, configuration.restores.get()); assertEquals(3, saved.get()); assertEquals(3, result.runs().size());
    }

    private ExperimentalBenchmarkCoordinator create(GamePerformanceCapture capture,
            ExperimentalConfigurationService configurations, BenchmarkPersistence persistence) {
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        return new ExperimentalBenchmarkCoordinator(() -> CompletableFuture.completedFuture(snapshot), capture,
                configurations, persistence, (state, message) -> { }, Duration.ZERO, 100);
    }
    private static GameProfile profile() { return new GameProfile("test", "Test", Set.of("game.exe"), null, null); }
    private static final class FakeConfiguration implements ExperimentalConfigurationService {
        final List<ExperimentConfiguration> applied = new ArrayList<>(); final AtomicInteger restores = new AtomicInteger();
        public OptimizationReport apply(GameProfile game, ExperimentConfiguration value) { applied.add(value); return new OptimizationReport(2, value.usesPriority() ? 1 : 0, false); }
        public OptimizationReport restore() { restores.incrementAndGet(); return new OptimizationReport(2, 1, true); }
    }
    private static final class FakeCapture implements GamePerformanceCapture {
        int remaining;
        FakeCapture(int remaining) { this.remaining = remaining; }
        public boolean isAvailable() { return true; } public void start(String process, Duration duration) { }
        public GamePerformanceResult stop() {
            if (remaining-- <= 0) throw new IllegalStateException("no result");
            return new GamePerformanceResult(OptionalDouble.of(100 + remaining), OptionalDouble.of(50), OptionalDouble.of(10),
                    OptionalDouble.empty(), OptionalDouble.empty(), 1000, Duration.ofSeconds(30), "game.exe", Instant.now());
        }
        public void close() { }
    }
}
