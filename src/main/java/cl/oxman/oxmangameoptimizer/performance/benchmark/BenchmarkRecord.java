package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.performance.PerformanceSnapshot;
import java.time.Instant;
import java.time.Duration;
import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;
import java.util.List;

public record BenchmarkRecord(String configurationId, String gameName, String processName, Instant timestamp,
        Duration captureDuration,
        GamePerformanceResult before, GamePerformanceResult after,
        PerformanceSnapshot systemBefore, PerformanceSnapshot systemAfter,
        OptimizationReport optimizationReport, int activeOptimizations,
        String oxmanVersion, String windowsVersion,
        String configurationA, String configurationB, String experimentType,
        int runNumber, String runOrder, List<String> activeOptimizationNames,
        ConfigurationValidity configurationValidity) {
    public BenchmarkRecord {
        activeOptimizationNames = activeOptimizationNames == null ? List.of() : List.copyOf(activeOptimizationNames);
        configurationValidity = configurationValidity == null ? ConfigurationValidity.VALID : configurationValidity;
    }
    public BenchmarkRecord(String configurationId, String gameName, String processName, Instant timestamp,
            Duration captureDuration, GamePerformanceResult before, GamePerformanceResult after,
            PerformanceSnapshot systemBefore, PerformanceSnapshot systemAfter,
            OptimizationReport optimizationReport, int activeOptimizations,
            String oxmanVersion, String windowsVersion,
            String configurationA, String configurationB, String experimentType,
            int runNumber, String runOrder, List<String> activeOptimizationNames) {
        this(configurationId, gameName, processName, timestamp, captureDuration, before, after,
                systemBefore, systemAfter, optimizationReport, activeOptimizations, oxmanVersion, windowsVersion,
                configurationA, configurationB, experimentType, runNumber, runOrder, activeOptimizationNames,
                ConfigurationValidity.VALID);
    }
    public BenchmarkRecord(String configurationId, String gameName, String processName, Instant timestamp,
            Duration captureDuration, GamePerformanceResult before, GamePerformanceResult after,
            PerformanceSnapshot systemBefore, PerformanceSnapshot systemAfter,
            OptimizationReport optimizationReport, int activeOptimizations,
            String oxmanVersion, String windowsVersion) {
        this(configurationId, gameName, processName, timestamp, captureDuration, before, after,
                systemBefore, systemAfter, optimizationReport, activeOptimizations, oxmanVersion, windowsVersion,
                "BASELINE", "SAFE_BOOST", "NONE", 1, "A -> B", List.of());
    }
}
