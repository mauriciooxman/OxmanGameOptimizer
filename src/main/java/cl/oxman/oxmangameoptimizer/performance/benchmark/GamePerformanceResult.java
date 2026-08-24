package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

public record GamePerformanceResult(OptionalDouble averageFps, OptionalDouble onePercentLow,
        OptionalDouble averageFrameTimeMs, OptionalDouble cpuFrameTimeMs,
        OptionalDouble gpuFrameTimeMs, long frameCount, Duration captureDuration,
        String processName, Instant timestamp) { }
