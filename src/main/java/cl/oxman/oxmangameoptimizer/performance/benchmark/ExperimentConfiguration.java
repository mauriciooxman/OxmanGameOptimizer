package cl.oxman.oxmangameoptimizer.performance.benchmark;

public enum ExperimentConfiguration {
    SAFE("Safe Boost", false, false, false),
    SAFE_PLUS_ABOVE_NORMAL("Safe Boost + ABOVE_NORMAL", true, false, false),
    SAFE_PLUS_HIGH_QOS("Safe Boost + HighQoS", false, true, false),
    SAFE_PLUS_BACKGROUND_ECOQOS("Safe Boost + Background EcoQoS", false, false, true);
    private final String label; private final boolean priority; private final boolean highQos; private final boolean background;
    ExperimentConfiguration(String label, boolean priority, boolean highQos, boolean background) {
        this.label = label; this.priority = priority; this.highQos = highQos; this.background = background;
    }
    public String label() { return label; }
    public boolean usesPriority() { return priority; }
    public boolean usesHighQos() { return highQos; }
    public boolean usesBackgroundEcoQos() { return background; }
    public boolean isExperimental() { return priority || highQos || background; }
}
