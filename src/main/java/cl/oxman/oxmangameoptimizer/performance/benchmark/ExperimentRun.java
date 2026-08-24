package cl.oxman.oxmangameoptimizer.performance.benchmark;

public record ExperimentRun(int runNumber, ExperimentOrder order,
                            GamePerformanceResult safe, GamePerformanceResult aboveNormal) { }
