package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.List;

public record ExperimentOrder(ExperimentConfiguration first, ExperimentConfiguration second) {
    public static ExperimentOrder forRun(int runNumber) {
        return forRun(runNumber, ExperimentConfiguration.SAFE_PLUS_HIGH_QOS);
    }
    public static ExperimentOrder forRun(int runNumber, ExperimentConfiguration experimental) {
        if (runNumber < 1) throw new IllegalArgumentException("Run number must be positive");
        if (experimental == null || !experimental.isExperimental())
            throw new IllegalArgumentException("An experimental configuration is required");
        return runNumber % 2 == 1
                ? new ExperimentOrder(ExperimentConfiguration.SAFE, experimental)
                : new ExperimentOrder(experimental, ExperimentConfiguration.SAFE);
    }
    public List<ExperimentConfiguration> configurations() { return List.of(first, second); }
    @Override public String toString() { return first.name() + " -> " + second.name(); }
}
