package cl.oxman.oxmangameoptimizer.performance.benchmark;

public record ExperimentRun(int runNumber, ExperimentOrder order,
                            GamePerformanceResult safe, GamePerformanceResult highQos,
                            ConfigurationValidity validity) {
    public ExperimentRun(int runNumber, ExperimentOrder order,
            GamePerformanceResult safe, GamePerformanceResult aboveNormal) {
        this(runNumber, order, safe, aboveNormal, ConfigurationValidity.VALID);
    }
    /** Compatibility accessor for persisted Experiment 1 consumers. */
    public GamePerformanceResult aboveNormal() { return highQos; }
}
