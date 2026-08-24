package cl.oxman.oxmangameoptimizer.performance.benchmark;

public enum ExperimentInterpretation {
    LIKELY_IMPROVEMENT("El resultado experimental sugiere una mejora."),
    NO_CLEAR_DIFFERENCE("No se observa una diferencia clara."),
    LIKELY_REGRESSION("El resultado experimental sugiere una regresión."),
    HIGH_VARIABILITY("Alta variabilidad entre runs. Repite el benchmark en condiciones más consistentes."),
    INSUFFICIENT_DATA("Datos insuficientes para interpretar el experimento.");
    private final String message;
    ExperimentInterpretation(String message) { this.message = message; }
    public String message() { return message; }
}
