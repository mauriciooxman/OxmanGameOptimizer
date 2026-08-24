package cl.oxman.oxmangameoptimizer.performance.benchmark;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentModelsTest {
    @Test void computesMeanMedianAndPopulationDeviation() {
        MetricSummary value = MetricSummary.of(List.of(OptionalDouble.of(1), OptionalDouble.of(2), OptionalDouble.of(3)));
        assertEquals(2, value.mean().orElseThrow(), 1e-9); assertEquals(2, value.median().orElseThrow(), 1e-9);
        assertEquals(Math.sqrt(2.0 / 3.0), value.standardDeviation().orElseThrow(), 1e-9);
    }
    @Test void ignoresMissingMetricsAndReportsCount() {
        MetricSummary value = MetricSummary.of(List.of(OptionalDouble.empty(), OptionalDouble.of(4)));
        assertEquals(1, value.count()); assertEquals(4, value.mean().orElseThrow());
    }
    @Test void alternatesAbBaReproducibly() {
        assertEquals(ExperimentConfiguration.SAFE, ExperimentOrder.forRun(1).first());
        assertEquals(ExperimentConfiguration.SAFE_PLUS_HIGH_QOS, ExperimentOrder.forRun(2).first());
        assertEquals(ExperimentOrder.forRun(1), ExperimentOrder.forRun(3));
    }
    @Test void consistentThreeRunImprovementIsLikelyImprovement() {
        assertEquals(ExperimentInterpretation.LIKELY_IMPROVEMENT,
                ExperimentResult.analyze(List.of(run(1, 100, 103), run(2, 101, 104), run(3, 99, 102))).interpretation());
    }
    @Test void opposingRunsAreHighVariabilityAndNeverDeleted() {
        var runs = List.of(run(1, 100, 108), run(2, 100, 94), run(3, 100, 101));
        var result = ExperimentResult.analyze(runs);
        assertEquals(ExperimentInterpretation.HIGH_VARIABILITY, result.interpretation()); assertEquals(3, result.runs().size());
    }
    @Test void missingAverageFpsIsInsufficientData() {
        GamePerformanceResult missing = result(OptionalDouble.empty(), 50, 10);
        assertEquals(ExperimentInterpretation.INSUFFICIENT_DATA, ExperimentResult.analyze(List.of(
                new ExperimentRun(1, ExperimentOrder.forRun(1), missing, missing))).interpretation());
    }
    @Test void noChangeIsNeverInterpretedAsImprovementOrRegressionAndIsExcluded() {
        ExperimentRun noChange = new ExperimentRun(1, ExperimentOrder.forRun(1),
                result(OptionalDouble.of(100), 50, 10), result(OptionalDouble.of(150), 70, 7),
                ConfigurationValidity.NO_CHANGE);
        ExperimentResult analyzed = ExperimentResult.analyze(List.of(noChange));
        assertEquals(ExperimentInterpretation.NO_CHANGE, analyzed.interpretation());
        assertEquals(0, analyzed.safe().averageFps().count());
        assertEquals(0, analyzed.highQos().averageFps().count());
    }
    private static ExperimentRun run(int number, double safe, double priority) {
        return new ExperimentRun(number, ExperimentOrder.forRun(number), result(OptionalDouble.of(safe), 50, 1000 / safe),
                result(OptionalDouble.of(priority), 51, 1000 / priority));
    }
    private static GamePerformanceResult result(OptionalDouble fps, double low, double frame) {
        return new GamePerformanceResult(fps, OptionalDouble.of(low), OptionalDouble.of(frame), OptionalDouble.empty(),
                OptionalDouble.empty(), 1000, Duration.ofSeconds(30), "game.exe", Instant.now());
    }
}
