package cl.oxman.oxmangameoptimizer.performance;

public record PerformanceSample(double cpuPercent, double ramUsedGb,
                                double ramAvailableGb, long processCount) { }
