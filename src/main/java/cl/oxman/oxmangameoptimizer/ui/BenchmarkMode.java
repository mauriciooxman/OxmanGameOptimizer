package cl.oxman.oxmangameoptimizer.ui;

public enum BenchmarkMode {
    NORMAL("BOOST NORMAL"),
    PROCESS_PRIORITY("EXPERIMENTO: PRIORIDAD"),
    HIGH_QOS("EXPERIMENTO: HIGH QOS"),
    BACKGROUND("EXPERIMENTO: CARGA DE FONDO");

    private final String label;

    BenchmarkMode(String label) {
        this.label = label;
    }

    public boolean isExperimental() {
        return this != NORMAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
