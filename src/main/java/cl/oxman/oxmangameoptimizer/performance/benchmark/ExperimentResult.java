package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.List;
import java.util.OptionalDouble;

public record ExperimentResult(List<ExperimentRun> runs, ConfigurationSummary safe,
                               ConfigurationSummary highQos, ExperimentInterpretation interpretation) {
    public ExperimentResult { runs = List.copyOf(runs); }
    /** Compatibility accessor for persisted Experiment 1 consumers. */
    public ConfigurationSummary aboveNormal() { return highQos; }

    public static ExperimentResult analyze(List<ExperimentRun> runs) {
        if (runs.stream().anyMatch(run -> run.validity() == ConfigurationValidity.NO_CHANGE))
            return new ExperimentResult(runs, emptySummary(), emptySummary(), ExperimentInterpretation.NO_CHANGE);
        List<ExperimentRun> valid = runs.stream().filter(run -> run.validity() == ConfigurationValidity.VALID).toList();
        ConfigurationSummary safe = ConfigurationSummary.from(valid.stream().map(ExperimentRun::safe).toList());
        ConfigurationSummary priority = ConfigurationSummary.from(valid.stream().map(ExperimentRun::highQos).toList());
        if (valid.size() != runs.size())
            return new ExperimentResult(runs, safe, priority, ExperimentInterpretation.CONFIGURATION_DRIFT);
        if (runs.isEmpty() || safe.averageFps().count() != runs.size() || priority.averageFps().count() != runs.size())
            return new ExperimentResult(runs, safe, priority, ExperimentInterpretation.INSUFFICIENT_DATA);
        if (variable(safe) || variable(priority) || inconsistent(runs))
            return new ExperimentResult(runs, safe, priority, ExperimentInterpretation.HIGH_VARIABILITY);
        double fps = change(safe.averageFps().mean(), priority.averageFps().mean());
        double low = change(safe.onePercentLow().mean(), priority.onePercentLow().mean());
        double frame = change(safe.frameTime().mean(), priority.frameTime().mean());
        long favorable = runs.stream().filter(run -> change(run.safe().averageFps(), run.highQos().averageFps()) > 0).count();
        ExperimentInterpretation value;
        if (fps >= 1 && low >= 0 && frame <= 0 && favorable == runs.size()) value = ExperimentInterpretation.LIKELY_IMPROVEMENT;
        else if (fps <= -1 && low <= 0 && frame >= 0 && favorable == 0) value = ExperimentInterpretation.LIKELY_REGRESSION;
        else value = ExperimentInterpretation.NO_CLEAR_DIFFERENCE;
        return new ExperimentResult(runs, safe, priority, value);
    }
    private static ConfigurationSummary emptySummary() { return ConfigurationSummary.from(List.of()); }
    private static boolean variable(ConfigurationSummary summary) {
        return summary.averageFps().coefficientOfVariation() > .05
                || summary.onePercentLow().coefficientOfVariation() > .10
                || summary.frameTime().coefficientOfVariation() > .05;
    }
    private static boolean inconsistent(List<ExperimentRun> runs) {
        if (runs.size() < 3) return false;
        long positive = runs.stream().filter(r -> change(r.safe().averageFps(), r.highQos().averageFps()) > 1).count();
        long negative = runs.stream().filter(r -> change(r.safe().averageFps(), r.highQos().averageFps()) < -1).count();
        return positive > 0 && negative > 0;
    }
    private static double change(OptionalDouble a, OptionalDouble b) {
        return a.isPresent() && b.isPresent() && a.getAsDouble() != 0
                ? (b.getAsDouble() - a.getAsDouble()) / a.getAsDouble() * 100 : 0;
    }

    public record ConfigurationSummary(MetricSummary averageFps, MetricSummary onePercentLow, MetricSummary frameTime) {
        static ConfigurationSummary from(List<GamePerformanceResult> values) {
            return new ConfigurationSummary(MetricSummary.of(values.stream().map(GamePerformanceResult::averageFps).toList()),
                    MetricSummary.of(values.stream().map(GamePerformanceResult::onePercentLow).toList()),
                    MetricSummary.of(values.stream().map(GamePerformanceResult::averageFrameTimeMs).toList()));
        }
    }
}
