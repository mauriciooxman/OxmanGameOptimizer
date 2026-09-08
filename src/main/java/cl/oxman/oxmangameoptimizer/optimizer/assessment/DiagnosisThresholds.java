package cl.oxman.oxmangameoptimizer.optimizer.assessment;

import java.time.Duration;

/** Policy thresholds, not predictions of performance gains. CPU is % of the whole machine. */
public record DiagnosisThresholds(int samples, Duration interval, double memoryNoticePercent,
        double memoryPressurePercent, double backgroundNoticePercent, double candidateAggregatePercent) {
    public static final DiagnosisThresholds DEFAULT = new DiagnosisThresholds(4, Duration.ofSeconds(1), 75, 85, 10, 3);
    public DiagnosisThresholds {
        if (samples < 3 || interval == null || interval.isNegative() || interval.isZero()
                || !Double.isFinite(memoryNoticePercent) || !Double.isFinite(memoryPressurePercent)
                || !Double.isFinite(backgroundNoticePercent) || !Double.isFinite(candidateAggregatePercent)
                || memoryNoticePercent <= 0 || memoryPressurePercent <= memoryNoticePercent || memoryPressurePercent > 100
                || backgroundNoticePercent <= 0 || backgroundNoticePercent > 100
                || candidateAggregatePercent < 3 || candidateAggregatePercent > 100)
            throw new IllegalArgumentException("Invalid conservative diagnosis thresholds");
    }
    public PerformanceOpportunity.Severity memorySeverity(double usedPercent) {
        if (!Double.isFinite(usedPercent) || usedPercent < 0 || usedPercent > 100) return PerformanceOpportunity.Severity.UNKNOWN;
        if (usedPercent > memoryPressurePercent) return PerformanceOpportunity.Severity.HIGH_PRESSURE;
        return usedPercent >= memoryNoticePercent ? PerformanceOpportunity.Severity.NOTICE : PerformanceOpportunity.Severity.OK;
    }
}
