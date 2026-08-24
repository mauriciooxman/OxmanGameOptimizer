package cl.oxman.oxmangameoptimizer.performance.benchmark;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FrameStatisticsTest {
    @Test void smallDatasetUsesWorstFrame() { assertEquals(10, FrameStatistics.onePercentLowFps(List.of(10d, 20d, 100d)).orElseThrow()); }
    @Test void largeDatasetAveragesWorstOnePercent() {
        List<Double> values = new ArrayList<>(); for (int i = 0; i < 198; i++) values.add(10d); values.add(50d); values.add(100d);
        assertEquals(1000d / 75d, FrameStatistics.onePercentLowFps(values).orElseThrow(), 0.001);
    }
    @Test void ignoresInvalidInputs() { assertEquals(50, FrameStatistics.onePercentLowFps(java.util.Arrays.asList(null, -1d, Double.NaN, 20d)).orElseThrow()); }
    @Test void emptyValidDatasetIsUnavailable() { assertTrue(FrameStatistics.onePercentLowFps(List.of(-1d, Double.NaN)).isEmpty()); }
}
