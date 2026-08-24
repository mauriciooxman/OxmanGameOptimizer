package cl.oxman.oxmangameoptimizer.performance;

public record PerformanceComparison(PerformanceSnapshot before, PerformanceSnapshot after) {
    public double cpuRelativeChangePercent() { return percent(before.cpuAverage(), after.cpuAverage()); }
    public double ramChangeGb() { return after.ramUsedAverage() - before.ramUsedAverage(); }
    public double processCountChange() { return after.processCountAverage() - before.processCountAverage(); }
    public boolean backgroundLoadReduced() {
        return after.cpuAverage() < before.cpuAverage() || after.processCountAverage() < before.processCountAverage();
    }
    static double percent(double before, double after) {
        return before == 0 || !Double.isFinite(before) || !Double.isFinite(after)
                ? Double.NaN : ((after - before) / before) * 100.0;
    }
}
