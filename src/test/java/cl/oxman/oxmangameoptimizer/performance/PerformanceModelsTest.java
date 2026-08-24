package cl.oxman.oxmangameoptimizer.performance;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceModelsTest {
    @Test void aggregatesCpuRamAndProcesses() {
        var snapshot = PerformanceSnapshot.from(List.of(new PerformanceSample(10, 6, 10, 100),
                new PerformanceSample(20, 8, 8, 120)), "Balanced");
        assertEquals(15, snapshot.cpuAverage()); assertEquals(10, snapshot.cpuMinimum());
        assertEquals(20, snapshot.cpuMaximum()); assertEquals(7, snapshot.ramUsedAverage());
        assertEquals(110, snapshot.processCountAverage()); assertEquals(2, snapshot.sampleCount());
    }
    @Test void emptySamplesAreExplicitlyUnavailable() {
        var snapshot = PerformanceSnapshot.from(List.of(), "unknown");
        assertEquals(0, snapshot.sampleCount()); assertTrue(Double.isNaN(snapshot.cpuAverage()));
    }
    @Test void comparisonHandlesSignsAndDivisionByZero() {
        var before = PerformanceSnapshot.from(List.of(new PerformanceSample(10, 8, 8, 100)), "A");
        var after = PerformanceSnapshot.from(List.of(new PerformanceSample(5, 7, 9, 90)), "B");
        assertEquals(-50, new PerformanceComparison(before, after).cpuRelativeChangePercent());
        var zero = PerformanceSnapshot.from(List.of(new PerformanceSample(0, 1, 1, 1)), "A");
        assertTrue(Double.isNaN(new PerformanceComparison(zero, after).cpuRelativeChangePercent()));
    }
}
