package cl.oxman.oxmangameoptimizer.performance.benchmark;

import cl.oxman.oxmangameoptimizer.optimizer.OptimizationReport;

/** Result of preparing one capture, including whether a causal comparison is possible. */
public record ConfigurationApplication(OptimizationReport report, ConfigurationValidity validity) {
    public ConfigurationApplication {
        if (report == null || validity == null) throw new IllegalArgumentException("report and validity are required");
    }

    public static ConfigurationApplication valid(OptimizationReport report) {
        return new ConfigurationApplication(report, ConfigurationValidity.VALID);
    }
}
