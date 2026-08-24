package cl.oxman.oxmangameoptimizer.ui;

public enum PrimaryStatus {
    READY("LISTO PARA JUGAR"), GAME_DETECTED("JUEGO DETECTADO"), OPTIMIZING("OPTIMIZANDO"),
    OPTIMIZED("OPTIMIZACIÓN ACTIVA"), RESTORING("RESTAURANDO"),
    RESTORED("SISTEMA RESTAURADO"), ERROR("ERROR");
    private final String label;
    PrimaryStatus(String label) { this.label = label; }
    public String label() { return label; }
}
