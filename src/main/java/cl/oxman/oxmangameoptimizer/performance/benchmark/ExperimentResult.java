package cl.oxman.oxmangameoptimizer.performance.benchmark;

import java.util.List;
import java.util.OptionalDouble;

public record ExperimentResult(List<ExperimentRun> runs, ConfigurationSummary safe,
                               ConfigurationSummary aboveNormal, ExperimentInterpretation interpretation) {
    public ExperimentResult { runs = List.copyOf(runs); }

    public static ExperimentResult analyze(List<ExperimentRun> runs) {
        ConfigurationSummary safe = ConfigurationSummary.from(runs.stream().map(ExperimentRun::safe).toList());
        ConfigurationSummary priority = ConfigurationSummary.from(runs.stream().map(ExperimentRun::aboveNormal).toList());
        if (runs.isEmpty() || safe.averageFps().count() != runs.size() || priority.averageFps().count() != runs.size())
            return new ExperimentResult(runs, safe, priority, ExperimentInterpretation.INSUFFICIENT_DATA);
        if (variable(safe) || variable(priority) || inconsistent(runs))
            return new ExperimentResult(runs, safe, priority, ExperimentInterpretation.HIGH_VARIABILITY);
        double fps = change(safe.averageFps().mean(), priority.averageFps().mean());
        double low = change(safe.onePercentLow().mean(), priority.onePercentLow().mean());
        double frame = change(safe.frameTime().mean(), priority.frameTime().mean());
        long favorable = runs.stream().filter(run -> change(run.safe().averageFps(), run.aboveNormal().averageFps()) > 0).count();
        ExperimentInterpretation value;
        if (fps >= 1 && low >= 0 && frame <= 0 && favorable == runs.size()) value = ExperimentInterpretation.LIKELY_IMPROVEMENT;
        else if (fps <= -1 && low <= 0 && frame >= 0 && favorable == 0) value = ExperimentInterpretation.LIKELY_REGRESSION;
        else value = ExperimentInterpretation.NO_CLEAR_DIFFERENCE;
        return new ExperimentResult(runs, safe, priority, value);
    }
    private static boolean variable(ConfigurationSummary summary) {
        return summary.averageFps().coefficientOfVariation() > .05
                || summary.onePercentLow().coefficientOfVariation() > .10
                || summary.frameTime().coefficientOfVariation() > .05;
    }
    private static boolean inconsistent(List<ExperimentRun> runs) {
        if (runs.size() < 3) return false;
        long positive = runs.stream().filter(r -> change(r.safe().averageFps(), r.aboveNormal().averageFps()) > 1).count();
        long negative = runs.stream().filter(r -> change(r.safe().averageFps(), r.aboveNormal().averageFps()) < -1).count();
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
