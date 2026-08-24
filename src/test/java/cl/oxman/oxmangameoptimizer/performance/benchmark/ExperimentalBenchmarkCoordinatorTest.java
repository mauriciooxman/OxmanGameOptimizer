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
        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_HIGH_QOS), configuration.applied);
        assertEquals(2, configuration.restores.get()); assertEquals(1, saved.get()); assertEquals(1, result.runs().size());
    }

    @Test void priorityExperimentUsesAboveNormalAndIdentifiesItselfInTheLog() {
        FakeConfiguration configuration = new FakeConfiguration();
        List<String> messages = new ArrayList<>();
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        coordinator = new ExperimentalBenchmarkCoordinator(() -> CompletableFuture.completedFuture(snapshot),
                new FakeCapture(2), configuration, record -> { }, (state, message) -> messages.add(message),
                ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL, Duration.ZERO, 100);

        coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();

        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL),
                configuration.applied);
        assertTrue(messages.contains("Experiment: Process Priority · Run 1/1 · SAFE -> SAFE_PLUS_ABOVE_NORMAL"));
    }

    @Test void highQosExperimentIdentifiesPowerThrottlingAndNeverAppliesPriorityConfiguration() {
        FakeConfiguration configuration = new FakeConfiguration();
        List<String> messages = new ArrayList<>();
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        coordinator = new ExperimentalBenchmarkCoordinator(() -> CompletableFuture.completedFuture(snapshot),
                new FakeCapture(2), configuration, record -> { }, (state, message) -> messages.add(message),
                ExperimentConfiguration.SAFE_PLUS_HIGH_QOS, Duration.ZERO, 100);

        coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();

        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_HIGH_QOS),
                configuration.applied);
        assertFalse(configuration.applied.contains(ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL));
        assertTrue(messages.contains("Experiment: Process Power Throttling · Run 1/1 · SAFE -> SAFE_PLUS_HIGH_QOS"));
    }

    @Test void backgroundExperimentUsesExistingAbFlowAndIdentifiesItself() {
        FakeConfiguration configuration = new FakeConfiguration(); List<String> messages = new ArrayList<>();
        PerformanceSnapshot snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 6, 10, 100)), "Balanced");
        coordinator = new ExperimentalBenchmarkCoordinator(() -> CompletableFuture.completedFuture(snapshot),
                new FakeCapture(2), configuration, record -> { }, (state, message) -> messages.add(message),
                ExperimentConfiguration.SAFE_PLUS_BACKGROUND_ECOQOS, Duration.ZERO, 100);
        coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();
        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_BACKGROUND_ECOQOS),
                configuration.applied);
        assertTrue(messages.contains("Experiment: Background Load Guard · Run 1/1 · SAFE -> SAFE_PLUS_BACKGROUND_ECOQOS"));
    }

    @Test void threeRunsAlternateAbBaAndRestoreBetweenAllSixCaptures() {
        FakeConfiguration configuration = new FakeConfiguration(); AtomicInteger saved = new AtomicInteger();
        coordinator = create(new FakeCapture(6), configuration, record -> saved.incrementAndGet());
        ExperimentResult result = coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 3).join();
        assertEquals(List.of(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_HIGH_QOS,
                ExperimentConfiguration.SAFE_PLUS_HIGH_QOS, ExperimentConfiguration.SAFE,
                ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_HIGH_QOS), configuration.applied);
        assertEquals(6, configuration.restores.get()); assertEquals(3, saved.get()); assertEquals(3, result.runs().size());
    }

    @Test void priorityDriftInvalidatesRunAndIsExcludedFromStatistics() {
        FakeConfiguration configuration = new FakeConfiguration(); configuration.drift = true;
        List<BenchmarkRecord> saved = new ArrayList<>();
        coordinator = create(new FakeCapture(2), configuration, saved::add);
        ExperimentResult result = coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();
        assertEquals(ConfigurationValidity.CONFIGURATION_DRIFT, result.runs().getFirst().validity());
        assertEquals(ExperimentInterpretation.CONFIGURATION_DRIFT, result.interpretation());
        assertEquals(0, result.safe().averageFps().count());
        assertEquals(0, result.highQos().averageFps().count());
        assertNotEquals(ExperimentInterpretation.LIKELY_IMPROVEMENT, result.interpretation());
        assertNotEquals(ExperimentInterpretation.LIKELY_REGRESSION, result.interpretation());
        assertEquals(ConfigurationValidity.CONFIGURATION_DRIFT, saved.getFirst().configurationValidity());
    }

    @Test void highQosAlreadyActiveStopsBeforeCaptureBAndPersistsNoChange() {
        FakeConfiguration configuration = new FakeConfiguration(); configuration.noChange = true;
        FakeCapture capture = new FakeCapture(2); List<BenchmarkRecord> saved = new ArrayList<>();
        coordinator = create(capture, configuration, saved::add);

        ExperimentResult result = coordinator.start(profile(), () -> Optional.of("game.exe"), Duration.ofSeconds(30), 1).join();

        assertEquals(1, capture.stops.get(), "only the SAFE capture must run");
        assertEquals(ExperimentInterpretation.NO_CHANGE, result.interpretation());
        assertEquals(ConfigurationValidity.NO_CHANGE, result.runs().getFirst().validity());
        assertEquals(0, result.safe().averageFps().count());
        assertEquals(0, result.highQos().averageFps().count());
        assertEquals(ConfigurationValidity.NO_CHANGE, saved.getFirst().configurationValidity());
        assertEquals("PROCESS_POWER_THROTTLING", saved.getFirst().experimentType());
        assertEquals(2, configuration.restores.get());
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
        boolean drift; boolean noChange;
        public ConfigurationApplication apply(GameProfile game, ExperimentConfiguration value) {
            applied.add(value);
            OptimizationReport report = new OptimizationReport(2, value.isExperimental() && !noChange ? 1 : 0, false);
            return new ConfigurationApplication(report, value.isExperimental() && noChange
                    ? ConfigurationValidity.NO_CHANGE : ConfigurationValidity.VALID);
        }
        public PriorityCaptureMonitor monitorPriorityDuringCapture() {
            boolean value = drift;
            return new PriorityCaptureMonitor() { public boolean drifted() { return value; } public void close() { } };
        }
        public OptimizationReport restore() { restores.incrementAndGet(); return new OptimizationReport(2, 1, true); }
    }
    private static final class FakeCapture implements GamePerformanceCapture {
        int remaining; final AtomicInteger stops = new AtomicInteger();
        FakeCapture(int remaining) { this.remaining = remaining; }
        public boolean isAvailable() { return true; } public void start(String process, Duration duration) { }
        public GamePerformanceResult stop() {
            stops.incrementAndGet();
            if (remaining-- <= 0) throw new IllegalStateException("no result");
            return new GamePerformanceResult(OptionalDouble.of(100 + remaining), OptionalDouble.of(50), OptionalDouble.of(10),
                    OptionalDouble.empty(), OptionalDouble.empty(), 1000, Duration.ofSeconds(30), "game.exe", Instant.now());
        }
        public void close() { }
    }
}
