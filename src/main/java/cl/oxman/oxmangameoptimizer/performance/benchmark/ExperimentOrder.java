package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.List;

public record ExperimentOrder(ExperimentConfiguration first, ExperimentConfiguration second) {
    public static ExperimentOrder forRun(int runNumber) {
        if (runNumber < 1) throw new IllegalArgumentException("Run number must be positive");
        return runNumber % 2 == 1
                ? new ExperimentOrder(ExperimentConfiguration.SAFE, ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL)
                : new ExperimentOrder(ExperimentConfiguration.SAFE_PLUS_ABOVE_NORMAL, ExperimentConfiguration.SAFE);
    }
    public List<ExperimentConfiguration> configurations() { return List.of(first, second); }
    @Override public String toString() { return first.name() + " -> " + second.name(); }
}
