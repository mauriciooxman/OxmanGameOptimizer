package cl.oxman.oxmangameoptimizer.performance.benchmark;

public enum ExperimentConfiguration {
    SAFE("Safe Boost", false), SAFE_PLUS_ABOVE_NORMAL("Safe Boost + ABOVE_NORMAL", true);
    private final String label; private final boolean priority;
    ExperimentConfiguration(String label, boolean priority) { this.label = label; this.priority = priority; }
    public String label() { return label; }
    public boolean usesPriority() { return priority; }
}
