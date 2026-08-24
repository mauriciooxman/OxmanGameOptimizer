package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.performance.PerformanceComparison;

public record BenchmarkOutcome(BenchmarkRecord record, GamePerformanceComparison gaming,
                               PerformanceComparison system, PerformanceInterpretation interpretation,
                               boolean persisted) { }
